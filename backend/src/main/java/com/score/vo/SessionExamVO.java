package com.score.vo;

import java.time.LocalDate;

public record SessionExamVO(Long examId, Long courseId, String courseName,
                            LocalDate examDate, Integer fullScore) {
}
