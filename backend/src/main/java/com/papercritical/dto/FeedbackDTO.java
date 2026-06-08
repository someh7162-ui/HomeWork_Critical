package com.papercritical.dto;

public class FeedbackDTO {
    private Integer rating;
    private String scene;
    private String content;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
