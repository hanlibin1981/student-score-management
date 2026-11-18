package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.common.Role;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Student;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import com.score.mapper.StudentMapper;
import com.score.security.SecurityUtils;
import com.score.vo.CourseComparisonVO;
import com.score.vo.DistributionItemVO;
import com.score.vo.FailingStudentVO;
import com.score.vo.OverallStatsVO;
import com.score.vo.OverviewVO;
import com.score.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String[] RANGES = {"90-100", "80-89", "70-79", "60-69", "<60"};

    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final CourseService courseService;

    public OverviewVO overview() {
        Long teacherId = scopeTeacherId();
        long studentCount = studentMapper.selectCount(null);
        long courseCount = teacherId == null
                ? courseMapper.selectCount(null)
                : courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId));

        OverallStatsVO stats = scoreMapper.selectOverallStats(teacherId);
        BigDecimal avgScore = stats == null || stats.getAvgScore() == null
                ? BigDecimal.ZERO : stats.getAvgScore();
        BigDecimal passRate = stats == null || stats.getPassRate() == null
                ? BigDecimal.ZERO : stats.getPassRate();
        return new OverviewVO(studentCount, courseCount, avgScore, passRate);
    }

    public List<DistributionItemVO> distribution(Long examId) {
        Exam exam = requireAccessibleExam(examId);
        Map<String, Integer> counts = scoreMapper.selectDistribution(exam.getId()).stream()
                .collect(Collectors.toMap(DistributionItemVO::getName, DistributionItemVO::getCount));
        return Arrays.stream(RANGES)
                .map(range -> {
                    DistributionItemVO item = new DistributionItemVO();
                    item.setName(range);
                    item.setCount(counts.getOrDefault(range, 0));
                    return item;
                })
                .toList();
    }

    public List<FailingStudentVO> failing(Long examId, BigDecimal threshold) {
        requireAccessibleExam(examId);
        return scoreMapper.selectFailing(examId, threshold);
    }

    public List<CourseComparisonVO> courseComparison() {
        return scoreMapper.selectCourseComparison(scopeTeacherId());
    }

    public List<TrendPointVO> trend(Long studentId, Long courseId) {
        if (courseId == null) {
            throw new BusinessException("请选择课程");
        }
        Long resolvedStudentId;
        if (Role.STUDENT.equals(SecurityUtils.current().role())) {
            resolvedStudentId = currentStudent().getId();
        } else {
            if (studentId == null) {
                throw new BusinessException("请选择学生");
            }
            courseService.requireAccessibleCourse(courseId);
            resolvedStudentId = studentId;
        }
        return scoreMapper.selectTrend(resolvedStudentId, courseId);
    }

    private Exam requireAccessibleExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw BusinessException.notFound("考试不存在");
        }
        courseService.requireAccessibleCourse(exam.getCourseId());
        return exam;
    }

    private Long scopeTeacherId() {
        return Role.TEACHER.equals(SecurityUtils.current().role())
                ? SecurityUtils.currentUserId() : null;
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
