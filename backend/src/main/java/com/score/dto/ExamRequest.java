package com.score.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamRequest {
    @NotNull(message = "请选择课程")
    private Long courseId;

    @NotBlank(message = "考试名称不能为空")
    private String name;

    private LocalDate examDate;

    @NotNull(message = "满分不能为空")
    @Min(value = 1, message = "满分必须大于 0")
    private Integer fullScore;
}
