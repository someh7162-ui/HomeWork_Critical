package com.papercritical.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class GradingResultDTO {
    private BigDecimal totalScore;
    private String overallComment;
    private BigDecimal confidenceScore;
    private String confidenceLevel;
    private Boolean reviewRequired;
    private List<DimensionScore> dimensions;
    private List<GrammarError> grammarErrors;
    private List<KnowledgePoint> knowledgePoints;
    private List<ImageAnnotation> annotations;

    @Data
    public static class DimensionScore {
        private String name;
        private BigDecimal score;
        private BigDecimal maxScore;
        private String comment;
    }

    @Data
    public static class GrammarError {
        private String original;
        private String correction;
        private String explanation;
        private String errorType;
        private String knowledgePoint;
        private String position;
    }

    @Data
    public static class KnowledgePoint {
        private String topic;
        private String description;
        private String suggestion;
    }

    @Data
    public static class ImageAnnotation {
        private Integer pageIndex;
        private String type;
        private BigDecimal x;
        private BigDecimal y;
        private BigDecimal width;
        private BigDecimal height;
        private String comment;
        private String severity;
        private String questionNo;
    }
}
