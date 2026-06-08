package com.papercritical.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class EssayReviewDTO {
    @NotNull(message = "最终得分不能为空")
    private BigDecimal finalScore;
    private String teacherReviewNote;
    private Map<String, Object> feedback;
}
