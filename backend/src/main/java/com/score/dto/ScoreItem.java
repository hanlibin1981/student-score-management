package com.score.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScoreItem {
    @NotNull(message = "学生不能为空")
    private Long studentId;

    @NotNull(message = "考试不能为空")
    private Long examId;

    @NotNull(message = "分数不能为空")
    @DecimalMin(value = "0", message = "分数不能为负数")
    private BigDecimal score;
}
