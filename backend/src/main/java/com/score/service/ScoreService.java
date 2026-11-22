package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.dto.ScoreBatchRequest;
import com.score.dto.ScoreItem;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.entity.Student;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import com.score.mapper.StudentMapper;
import com.score.security.SecurityUtils;
import com.score.util.StatsCalculator;
import com.score.vo.ExamScoreRowVO;
import com.score.vo.MyCourseVO;
import com.score.vo.SessionExamVO;
import com.score.vo.SessionScoreRowVO;
import com.score.vo.SessionScoreVO;
import com.score.vo.StudentCourseTotalVO;
import com.score.vo.StudentTotalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreMapper scoreMapper;
    private final ExamMapper examMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final CourseService courseService;

    public List<ExamScoreRowVO> listByExam(Long examId, String className) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw BusinessException.notFound("考试不存在");
        }
        courseService.requireAccessibleCourse(exam.getCourseId());
        return scoreMapper.selectExamScoreRows(examId, className);
    }

    /**
     * 把同名考试聚合为一场考试：每名学生一行，各课程成绩放在同一行的 score map 中。
     * 管理员可看到全部课程，教师只看到自己所授课程。
     */
    public SessionScoreVO sessionScores(String examName, String className) {
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getName, examName)
                .orderByAsc(Exam::getCourseId)
                .orderByAsc(Exam::getId));
        if (exams.isEmpty()) {
            return emptySession(examName);
        }

        Map<Long, Course> courses = courseMapper.selectBatchIds(
                        exams.stream().map(Exam::getCourseId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        boolean admin = SecurityUtils.isAdmin();
        Long currentUserId = admin ? null : SecurityUtils.currentUserId();
        List<Exam> accessibleExams = exams.stream()
                .filter(exam -> courses.containsKey(exam.getCourseId()))
                .filter(exam -> admin || Objects.equals(
                        courses.get(exam.getCourseId()).getTeacherId(), currentUserId))
                .toList();
        if (accessibleExams.isEmpty()) {
            return emptySession(examName);
        }

        List<SessionExamVO> examColumns = accessibleExams.stream()
                .map(exam -> {
                    Course course = courses.get(exam.getCourseId());
                    return new SessionExamVO(exam.getId(), exam.getCourseId(), course.getName(),
                            exam.getExamDate(), exam.getFullScore());
                })
                .toList();
        Set<Long> examIds = accessibleExams.stream()
                .map(Exam::getId)
                .collect(Collectors.toSet());
        return new SessionScoreVO(examName, examColumns, buildSessionRows(examIds, className));
    }

    private List<SessionScoreRowVO> buildSessionRows(Set<Long> examIds, String className) {
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(StringUtils.hasText(className), Student::getClassName, className)
                .orderByAsc(Student::getStudentNo));

        Map<Long, Map<Long, BigDecimal>> scoresByStudent = new HashMap<>();
        if (!students.isEmpty()) {
            for (Score score : scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                    .in(Score::getExamId, examIds))) {
                scoresByStudent.computeIfAbsent(score.getStudentId(), key -> new HashMap<>())
                        .put(score.getExamId(), score.getScore());
            }
        }

        return students.stream()
                .map(student -> new SessionScoreRowVO(student.getId(), student.getStudentNo(),
                        student.getName(), student.getClassName(),
                        scoresByStudent.getOrDefault(student.getId(), Map.of())))
                .toList();
    }

    private SessionScoreVO emptySession(String examName) {
        return new SessionScoreVO(examName, List.of(), List.of());
    }

    @Transactional
    public int saveBatch(ScoreBatchRequest request) {
        List<ScoreItem> items = request.getItems();
        if (items.isEmpty()) {
            throw new BusinessException("没有需要保存的成绩");
        }
        Map<Long, Exam> exams = new HashMap<>();
        for (ScoreItem item : items) {
            Exam exam = exams.get(item.getExamId());
            if (exam == null) {
                exam = examMapper.selectById(item.getExamId());
                if (exam == null) {
                    throw BusinessException.notFound("考试不存在");
                }
                exams.put(exam.getId(), exam);
            }
            courseService.requireAccessibleCourse(exam.getCourseId());

            if (item.getScore().compareTo(BigDecimal.valueOf(exam.getFullScore())) > 0) {
                throw new BusinessException("分数不能超过满分 " + exam.getFullScore());
            }
            if (studentMapper.selectById(item.getStudentId()) == null) {
                throw BusinessException.notFound("学生不存在");
            }

            Score existing = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                    .eq(Score::getStudentId, item.getStudentId())
                    .eq(Score::getExamId, item.getExamId()));
            if (existing != null) {
                Score update = new Score();
                update.setId(existing.getId());
                update.setScore(item.getScore());
                update.setUpdatedAt(LocalDateTime.now());
                scoreMapper.updateById(update);
            } else {
                Score score = new Score();
                score.setStudentId(item.getStudentId());
                score.setExamId(item.getExamId());
                score.setScore(item.getScore());
                score.setUpdatedAt(LocalDateTime.now());
                scoreMapper.insert(score);
            }
        }
        return items.size();
    }

    public void delete(Long id) {
        Score score = scoreMapper.selectById(id);
        if (score == null) {
            throw BusinessException.notFound("成绩不存在");
        }
        Exam exam = examMapper.selectById(score.getExamId());
        if (exam != null) {
            courseService.requireAccessibleCourse(exam.getCourseId());
        }
        scoreMapper.deleteById(id);
    }

    public List<MyCourseVO> myCourses() {
        Student student = currentStudent();
        List<StudentCourseTotalVO> totals = scoreMapper.selectStudentCourseTotals(student.getId());
        List<MyCourseVO> result = new ArrayList<>();
        for (StudentCourseTotalVO item : totals) {
            List<BigDecimal> allTotals = scoreMapper.selectCourseTotals(item.getCourseId())
                    .stream().map(StudentTotalVO::getTotal).toList();
            long rank = StatsCalculator.rank(item.getTotal(), allTotals);
            result.add(new MyCourseVO(item.getCourseId(), item.getCourseName(), item.getTotal(),
                    rank, allTotals.size()));
        }
        return result;
    }

    private Student currentStudent() {
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, SecurityUtils.currentUserId()));
        if (student == null) {
            throw BusinessException.notFound("当前账号未绑定学生档案");
        }
        return student;
    }
}
