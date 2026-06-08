package com.papercritical.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("tb_assignment")
public class Assignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String title;
    private String assignmentType;
    private String question;
    private String modelEssay;
    private String questionRange;
    private String answerKeyImages;
    private String answerKeyJson;
    private String answerKeyMineruMarkdown;
    private String answerKeyMineruStructureJson;
    private String answerKeyStatus;
    private String answerKeyMessage;
    private Integer wordLimitMin;
    private Integer wordLimitMax;
    private Integer totalScore;
    private String scoringCriteria;
    private String className;
    private String subject;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAssignmentType() { return assignmentType; }
    public void setAssignmentType(String assignmentType) { this.assignmentType = assignmentType; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getModelEssay() { return modelEssay; }
    public void setModelEssay(String modelEssay) { this.modelEssay = modelEssay; }
    public String getQuestionRange() { return questionRange; }
    public void setQuestionRange(String questionRange) { this.questionRange = questionRange; }
    public String getAnswerKeyImages() { return answerKeyImages; }
    public void setAnswerKeyImages(String answerKeyImages) { this.answerKeyImages = answerKeyImages; }
    public String getAnswerKeyJson() { return answerKeyJson; }
    public void setAnswerKeyJson(String answerKeyJson) { this.answerKeyJson = answerKeyJson; }
    public String getAnswerKeyMineruMarkdown() { return answerKeyMineruMarkdown; }
    public void setAnswerKeyMineruMarkdown(String answerKeyMineruMarkdown) { this.answerKeyMineruMarkdown = answerKeyMineruMarkdown; }
    public String getAnswerKeyMineruStructureJson() { return answerKeyMineruStructureJson; }
    public void setAnswerKeyMineruStructureJson(String answerKeyMineruStructureJson) { this.answerKeyMineruStructureJson = answerKeyMineruStructureJson; }
    public String getAnswerKeyStatus() { return answerKeyStatus; }
    public void setAnswerKeyStatus(String answerKeyStatus) { this.answerKeyStatus = answerKeyStatus; }
    public String getAnswerKeyMessage() { return answerKeyMessage; }
    public void setAnswerKeyMessage(String answerKeyMessage) { this.answerKeyMessage = answerKeyMessage; }
    public Integer getWordLimitMin() { return wordLimitMin; }
    public void setWordLimitMin(Integer wordLimitMin) { this.wordLimitMin = wordLimitMin; }
    public Integer getWordLimitMax() { return wordLimitMax; }
    public void setWordLimitMax(Integer wordLimitMax) { this.wordLimitMax = wordLimitMax; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public String getScoringCriteria() { return scoringCriteria; }
    public void setScoringCriteria(String scoringCriteria) { this.scoringCriteria = scoringCriteria; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
