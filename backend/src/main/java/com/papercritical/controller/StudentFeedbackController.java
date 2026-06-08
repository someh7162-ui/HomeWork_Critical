package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.dto.FeedbackDTO;
import com.papercritical.entity.UserFeedback;
import com.papercritical.service.FeedbackService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/feedback")
public class StudentFeedbackController {

    private final FeedbackService feedbackService;

    public StudentFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        try {
            return Result.ok(feedbackService.listForStudent(studentId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        try {
            return Result.ok(feedbackService.summaryForStudent(studentId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping
    public Result<UserFeedback> create(@RequestBody FeedbackDTO dto, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        try {
            return Result.ok(feedbackService.createStudentFeedback(studentId, dto));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
