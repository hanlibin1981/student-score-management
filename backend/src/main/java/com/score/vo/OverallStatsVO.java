package com.score.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OverallStatsVO {
    private Long totalScores;
    private BigDecimal avgScore;
    private BigDecimal passRate;
}
