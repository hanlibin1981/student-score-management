package com.score.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusRequest {
    @NotNull(message = "请指定启用状态")
    private Boolean enabled;
}
