package com.papercritical.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("tb_essay")
public class Essay {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private Long teacherId;
    private String contentHash;
    private String imageUrl;
    private String imageUrls;
    private String annotatedImageUrl;
    private String annotatedImageUrls;
    private String ocrText;
    private String mineruMarkdown;
    private String mineruStructureJson;
    private BigDecimal aiScore;
    private BigDecimal finalScore;
    private BigDecimal confidenceScore;
    private String confidenceLevel;
    private Boolean reviewRequired;
    private String teacherReviewNote;
    private String aiFeedback;
    private String aiRawResponse;
    private String workbookResultJson;
    private String status;
    private String failureReason;
    private String failureMessage;
    private LocalDateTime gradingStartedAt;
    private LocalDateTime gradingCompletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String studentName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }
    public String getAnnotatedImageUrl() { return annotatedImageUrl; }
    public void setAnnotatedImageUrl(String annotatedImageUrl) { this.annotatedImageUrl = annotatedImageUrl; }
    public String getAnnotatedImageUrls() { return annotatedImageUrls; }
    public void setAnnotatedImageUrls(String annotatedImageUrls) { this.annotatedImageUrls = annotatedImageUrls; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public String getMineruMarkdown() { return mineruMarkdown; }
    public void setMineruMarkdown(String mineruMarkdown) { this.mineruMarkdown = mineruMarkdown; }
    public String getMineruStructureJson() { return mineruStructureJson; }
    public void setMineruStructureJson(String mineruStructureJson) { this.mineruStructureJson = mineruStructureJson; }
    public BigDecimal getAiScore() { return aiScore; }
    public void setAiScore(BigDecimal aiScore) { this.aiScore = aiScore; }
    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(String confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    public Boolean getReviewRequired() { return reviewRequired; }
    public void setReviewRequired(Boolean reviewRequired) { this.reviewRequired = reviewRequired; }
    public String getTeacherReviewNote() { return teacherReviewNote; }
    public void setTeacherReviewNote(String teacherReviewNote) { this.teacherReviewNote = teacherReviewNote; }
    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }
    public String getAiRawResponse() { return aiRawResponse; }
    public void setAiRawResponse(String aiRawResponse) { this.aiRawResponse = aiRawResponse; }
    public String getWorkbookResultJson() { return workbookResultJson; }
    public void setWorkbookResultJson(String workbookResultJson) { this.workbookResultJson = workbookResultJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public LocalDateTime getGradingStartedAt() { return gradingStartedAt; }
    public void setGradingStartedAt(LocalDateTime gradingStartedAt) { this.gradingStartedAt = gradingStartedAt; }
    public LocalDateTime getGradingCompletedAt() { return gradingCompletedAt; }
    public void setGradingCompletedAt(LocalDateTime gradingCompletedAt) { this.gradingCompletedAt = gradingCompletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
}
