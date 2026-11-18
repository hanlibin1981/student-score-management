package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StudentTotalVO {
    private Long studentId;
    private BigDecimal total;
}
