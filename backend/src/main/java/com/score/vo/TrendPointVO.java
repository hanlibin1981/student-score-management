package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TrendPointVO {
    private String examName;
    private LocalDate examDate;
    private BigDecimal percent;
}
