package com.score.vo;

import java.math.BigDecimal;
import java.util.Map;

public record SessionScoreRowVO(Long studentId, String studentNo, String studentName,
                                String className, Map<Long, BigDecimal> scores) {
}
