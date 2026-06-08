package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.entity.Essay;
import com.papercritical.service.AssignmentSubmissionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentPortalController {

    private final AssignmentSubmissionService assignmentSubmissionService;

    public StudentPortalController(AssignmentSubmissionService assignmentSubmissionService) {
        this.assignmentSubmissionService = assignmentSubmissionService;
    }

    @GetMapping("/assignments")
    public Result<List<Map<String, Object>>> assignments(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        return Result.ok(assignmentSubmissionService.listStudentAssignments(studentId));
    }

    @GetMapping("/assignments/{assignmentId}")
    public Result<Map<String, Object>> detail(@PathVariable Long assignmentId,
                                              HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        return Result.ok(assignmentSubmissionService.studentAssignmentDetail(assignmentId, studentId));
    }

    @PostMapping("/assignments/{assignmentId}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long assignmentId,
                                              @RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        Map<String, Object> result = assignmentSubmissionService.submit(assignmentId, studentId, file);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Result.ok(result);
        }
        return Result.error((String) result.get("message"));
    }

    @PostMapping("/assignments/{assignmentId}/workbook-submit")
    public Result<Map<String, Object>> submitWorkbook(@PathVariable Long assignmentId,
                                                      @RequestParam("files") MultipartFile[] files,
                                                      HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        Map<String, Object> result = assignmentSubmissionService.submitWorkbook(assignmentId, studentId, files);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Result.ok(result);
        }
        return Result.error((String) result.get("message"));
    }

    @GetMapping("/essays/{essayId}/progress")
    public Result<Map<String, Object>> progress(@PathVariable Long essayId,
                                                HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        return Result.ok(assignmentSubmissionService.studentEssayProgress(essayId, studentId));
    }

    @GetMapping("/essays/{essayId}")
    public Result<Essay> essay(@PathVariable Long essayId, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        return Result.ok(assignmentSubmissionService.studentEssayDetail(essayId, studentId));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("studentId");
        return Result.ok(assignmentSubmissionService.getStudentSummary(studentId));
    }
}
