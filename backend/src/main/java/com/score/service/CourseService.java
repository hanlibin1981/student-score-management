package com.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.score.common.BusinessException;
import com.score.common.Role;
import com.score.dto.CourseRequest;
import com.score.entity.Course;
import com.score.entity.Exam;
import com.score.entity.User;
import com.score.mapper.CourseMapper;
import com.score.mapper.ExamMapper;
import com.score.mapper.UserMapper;
import com.score.security.SecurityUtils;
import com.score.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final ExamMapper examMapper;
    private final UserMapper userMapper;

    public List<CourseVO> list(String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .like(StringUtils.hasText(keyword), Course::getName, keyword)
                .orderByAsc(Course::getId);
        if (Role.TEACHER.equals(SecurityUtils.current().role())) {
            wrapper.eq(Course::getTeacherId, SecurityUtils.currentUserId());
        }
        List<Course> courses = courseMapper.selectList(wrapper);

        Set<Long> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> teacherNames = teacherIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName));

        return courses.stream()
                .map(course -> CourseVO.from(course, teacherNames.get(course.getTeacherId())))
                .toList();
    }

    public CourseVO create(CourseRequest request) {
        long exists = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                .eq(Course::getCourseCode, request.getCourseCode()));
        if (exists > 0) {
            throw BusinessException.conflict("课程编号已存在");
        }
        Course course = new Course();
        apply(course, request);
        courseMapper.insert(course);
        return detail(course);
    }

    public CourseVO update(Long id, CourseRequest request) {
        requireCourse(id);
        long exists = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                .eq(Course::getCourseCode, request.getCourseCode())
                .ne(Course::getId, id));
        if (exists > 0) {
            throw BusinessException.conflict("课程编号已存在");
        }
        Course update = new Course();
        update.setId(id);
        apply(update, request);
        courseMapper.updateById(update);
        return detail(requireCourse(id));
    }

    public void delete(Long id) {
        requireCourse(id);
        long examCount = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getCourseId, id));
        if (examCount > 0) {
            throw BusinessException.conflict("该课程已有考试记录，不能删除");
        }
        courseMapper.deleteById(id);
    }

    /**
     * 管理员可操作任意课程；教师只能操作自己的课程。
     */
    public Course requireAccessibleCourse(Long courseId) {
        Course course = requireCourse(courseId);
        if (!SecurityUtils.isAdmin()
                && !Objects.equals(course.getTeacherId(), SecurityUtils.currentUserId())) {
            throw BusinessException.forbidden("无权操作该课程");
        }
        return course;
    }

    private Course requireCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw BusinessException.notFound("课程不存在");
        }
        return course;
    }

    private CourseVO detail(Course course) {
        String teacherName = null;
        if (course.getTeacherId() != null) {
            User teacher = userMapper.selectById(course.getTeacherId());
            teacherName = teacher == null ? null : teacher.getRealName();
        }
        return CourseVO.from(course, teacherName);
    }

    private void apply(Course course, CourseRequest request) {
        course.setCourseCode(request.getCourseCode());
        course.setName(request.getName());
        course.setCredit(request.getCredit());
        course.setSemester(request.getSemester());
        course.setTeacherId(request.getTeacherId());
    }
}
