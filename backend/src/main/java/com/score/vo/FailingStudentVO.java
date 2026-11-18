package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FailingStudentVO {
    private String studentNo;
    private String name;
    private BigDecimal score;
    private Integer fullScore;
}
