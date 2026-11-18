package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StudentCourseTotalVO {
    private Long courseId;
    private String courseName;
    private BigDecimal total;
}
