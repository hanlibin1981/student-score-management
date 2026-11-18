package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.dto.ExamRequest;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.Score;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.ScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamMapper examMapper;
    private final CourseMapper courseMapper;
    private final ScoreMapper scoreMapper;
    private final CourseService courseService;

    public List<Exam> listByCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw BusinessException.notFound("课程不存在");
        }
        return examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, courseId)
                .orderByAsc(Exam::getExamDate)
                .orderByAsc(Exam::getId));
    }

    public Exam create(ExamRequest request) {
        courseService.requireAccessibleCourse(request.getCourseId());
        long exists = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, request.getCourseId())
                .eq(Exam::getName, request.getName()));
        if (exists > 0) {
            throw BusinessException.conflict("该课程下已存在同名考试");
        }
        Exam exam = new Exam();
        exam.setCourseId(request.getCourseId());
        apply(exam, request);
        examMapper.insert(exam);
        return exam;
    }

    public Exam update(Long id, ExamRequest request) {
        Exam exam = requireExam(id);
        courseService.requireAccessibleCourse(exam.getCourseId());
        long exists = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, exam.getCourseId())
                .eq(Exam::getName, request.getName())
                .ne(Exam::getId, id));
        if (exists > 0) {
            throw BusinessException.conflict("该课程下已存在同名考试");
        }
        Exam update = new Exam();
        update.setId(id);
        apply(update, request);
        examMapper.updateById(update);
        return examMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        Exam exam = requireExam(id);
        courseService.requireAccessibleCourse(exam.getCourseId());
        scoreMapper.delete(new LambdaQueryWrapper<Score>().eq(Score::getExamId, id));
        examMapper.deleteById(id);
    }

    public Exam requireExam(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw BusinessException.notFound("考试不存在");
        }
        return exam;
    }

    private void apply(Exam exam, ExamRequest request) {
        exam.setName(request.getName());
        exam.setExamDate(request.getExamDate());
        exam.setFullScore(request.getFullScore());
    }
}
