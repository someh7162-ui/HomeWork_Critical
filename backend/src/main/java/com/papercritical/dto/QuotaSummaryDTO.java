package com.papercritical.dto;

public class QuotaSummaryDTO {
    private Integer freeTotal;
    private Integer freeUsed;
    private Integer freeRemaining;
    private Integer paidTotal;
    private Integer paidUsed;
    private Integer paidRemaining;
    private Integer remainingTotal;
    private String status;

    public Integer getFreeTotal() {
        return freeTotal;
    }

    public void setFreeTotal(Integer freeTotal) {
        this.freeTotal = freeTotal;
    }

    public Integer getFreeUsed() {
        return freeUsed;
    }

    public void setFreeUsed(Integer freeUsed) {
        this.freeUsed = freeUsed;
    }

    public Integer getFreeRemaining() {
        return freeRemaining;
    }

    public void setFreeRemaining(Integer freeRemaining) {
        this.freeRemaining = freeRemaining;
    }

    public Integer getPaidTotal() {
        return paidTotal;
    }

    public void setPaidTotal(Integer paidTotal) {
        this.paidTotal = paidTotal;
    }

    public Integer getPaidUsed() {
        return paidUsed;
    }

    public void setPaidUsed(Integer paidUsed) {
        this.paidUsed = paidUsed;
    }

    public Integer getPaidRemaining() {
        return paidRemaining;
    }

    public void setPaidRemaining(Integer paidRemaining) {
        this.paidRemaining = paidRemaining;
    }

    public Integer getRemainingTotal() {
        return remainingTotal;
    }

    public void setRemainingTotal(Integer remainingTotal) {
        this.remainingTotal = remainingTotal;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
