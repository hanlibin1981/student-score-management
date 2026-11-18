package com.score.vo;

import java.math.BigDecimal;

public record OverviewVO(long studentCount, long courseCount,
                         BigDecimal avgScore, BigDecimal passRate) {
}
