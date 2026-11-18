package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseComparisonVO {
    private Long courseId;
    private String courseName;
    private BigDecimal avgScore;
    private BigDecimal passRate;
}
