package com.score.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ScoreBatchRequest {
    @NotEmpty(message = "没有需要保存的成绩")
    @Valid
    private List<ScoreItem> items;
}
