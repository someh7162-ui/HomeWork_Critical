package com.papercritical.dto;

import java.math.BigDecimal;
import java.util.Map;

public class PaymentOrderDTO {
    private String orderNo;
    private String packageName;
    private String payChannel;
    private String paymentMode;
    private BigDecimal amount;
    private Integer quotaAmount;
    private String status;
    private String message;
    private Map<String, Object> paymentParams;
    private QuotaSummaryDTO quotaSummary;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getQuotaAmount() {
        return quotaAmount;
    }

    public void setQuotaAmount(Integer quotaAmount) {
        this.quotaAmount = quotaAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getPaymentParams() {
        return paymentParams;
    }

    public void setPaymentParams(Map<String, Object> paymentParams) {
        this.paymentParams = paymentParams;
    }

    public QuotaSummaryDTO getQuotaSummary() {
        return quotaSummary;
    }

    public void setQuotaSummary(QuotaSummaryDTO quotaSummary) {
        this.quotaSummary = quotaSummary;
    }
}
