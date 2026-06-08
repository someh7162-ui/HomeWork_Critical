package com.papercritical.exception;

import com.papercritical.dto.QuotaSummaryDTO;

public class QuotaInsufficientException extends RuntimeException {

    private final QuotaSummaryDTO quotaSummary;

    public QuotaInsufficientException(String message, QuotaSummaryDTO quotaSummary) {
        super(message);
        this.quotaSummary = quotaSummary;
    }

    public QuotaSummaryDTO getQuotaSummary() {
        return quotaSummary;
    }
}
