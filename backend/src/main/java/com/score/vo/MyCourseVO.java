package com.score.vo;

import java.math.BigDecimal;

public record MyCourseVO(Long courseId, String courseName, BigDecimal totalScore,
                         long rank, long studentCount) {
}
