package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExamScoreRowVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String className;
    private Long scoreId;
    private BigDecimal score;
}
