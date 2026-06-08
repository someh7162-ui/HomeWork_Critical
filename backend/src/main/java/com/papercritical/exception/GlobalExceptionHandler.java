package com.papercritical.exception;

import com.papercritical.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("参数校验失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleFileSize() {
        return Result.error(400, "文件大小超过限制（最大20MB）");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(QuotaInsufficientException.class)
    public Result<?> handleQuotaInsufficient(QuotaInsufficientException e) {
        return Result.error(402, e.getMessage(), e.getQuotaSummary());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Unexpected error", e);
        return Result.error("服务器内部错误: " + e.getMessage());
    }
}
