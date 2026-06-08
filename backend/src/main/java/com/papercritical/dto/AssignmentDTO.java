package com.papercritical.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AssignmentDTO {
    private String assignmentType;

    @NotBlank(message = "作业标题不能为空")
    private String title;

    private String question;
    private String modelEssay;
    private String questionRange;
    private Integer wordLimitMin;
    private Integer wordLimitMax;

    @NotNull(message = "总分不能为空")
    private Integer totalScore;

    private String scoringCriteria;

    @NotBlank(message = "请选择班级")
    private String className;

    @NotBlank(message = "请选择科目")
    private String subject;
}
