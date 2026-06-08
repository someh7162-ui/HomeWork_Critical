package com.papercritical.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class FinalScoreDTO {
    @NotNull(message = "最终得分不能为空")
    @DecimalMin(value = "0", message = "最终得分不能小于0")
    private BigDecimal finalScore;
}
