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
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(feedbackService.listForTeacher(teacherId));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(feedbackService.summaryForTeacher(teacherId));
    }

    @PostMapping
    public Result<UserFeedback> create(@RequestBody FeedbackDTO dto, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        try {
            return Result.ok(feedbackService.createTeacherFeedback(teacherId, dto));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
