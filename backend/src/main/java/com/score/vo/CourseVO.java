package com.score.vo;

import com.score.entity.Course;

import java.math.BigDecimal;

public record CourseVO(Long id, String courseCode, String name, BigDecimal credit,
                       String semester, Long teacherId, String teacherName) {

    public static CourseVO from(Course course, String teacherName) {
        return new CourseVO(course.getId(), course.getCourseCode(), course.getName(),
                course.getCredit(), course.getSemester(), course.getTeacherId(), teacherName);
    }
}
