package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.dto.FinalScoreDTO;
import com.papercritical.dto.EssayReviewDTO;
import com.papercritical.entity.Essay;
import com.papercritical.service.AssignmentSubmissionService;
import com.papercritical.service.EssayService;
import com.papercritical.service.ExcelExportService;
import com.papercritical.service.StatsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/essays")
public class EssayController {

    private final EssayService essayService;
    private final ExcelExportService excelExportService;
    private final StatsService statsService;
    private final AssignmentSubmissionService assignmentSubmissionService;

    public EssayController(EssayService essayService, ExcelExportService excelExportService,
                           StatsService statsService,
                           AssignmentSubmissionService assignmentSubmissionService) {
        this.essayService = essayService;
        this.excelExportService = excelExportService;
        this.statsService = statsService;
        this.assignmentSubmissionService = assignmentSubmissionService;
    }

    @GetMapping("/assignment/{assignmentId}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long assignmentId,
                                          @RequestParam(defaultValue = "成绩单") String title,
                                          HttpServletRequest request) throws Exception {
        Long teacherId = (Long) request.getAttribute("teacherId");
        byte[] data = excelExportService.exportEssays(assignmentId, teacherId, title);
        String filename = URLEncoder.encode(title + ".xlsx", StandardCharsets.UTF_8.toString())
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/assignment/{assignmentId}/stats")
    public Result<Map<String, Object>> stats(@PathVariable Long assignmentId,
                                              HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(statsService.getAssignmentStats(assignmentId, teacherId));
    }

    @GetMapping("/teacher-dashboard")
    public Result<Map<String, Object>> teacherDashboard(HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(statsService.getTeacherInsightDashboard(teacherId));
    }

    @GetMapping("/assignment/{assignmentId}/submissions")
    public Result<Map<String, Object>> submissions(@PathVariable Long assignmentId,
                                                   HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentSubmissionService.teacherSubmissionSummary(assignmentId, teacherId));
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadAndGrade(
            @RequestParam("file") MultipartFile file,
            @RequestParam("assignmentId") Long assignmentId,
            @RequestParam("studentId") Long studentId,
            HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        Map<String, Object> result = essayService.uploadAndGrade(file, assignmentId, studentId, teacherId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Result.ok(result);
        }
        return Result.error((String) result.get("message"));
    }

    @PostMapping("/upload-workbook")
    public Result<Map<String, Object>> uploadWorkbook(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("assignmentId") Long assignmentId,
            @RequestParam("studentId") Long studentId,
            HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        Map<String, Object> result = essayService.uploadAndGradeWorkbook(files, assignmentId, studentId, teacherId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Result.ok(result);
        }
        return Result.error((String) result.get("message"));
    }

    @GetMapping("/{id}")
    public Result<Essay> detail(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(essayService.getDetail(id, teacherId));
    }

    @GetMapping("/assignment/{assignmentId}")
    public Result<List<Essay>> listByAssignment(@PathVariable Long assignmentId, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(essayService.listByAssignment(assignmentId, teacherId));
    }

    @GetMapping("/{id}/progress")
    public Result<Map<String, Object>> progress(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(essayService.getProgress(id, teacherId));
    }

    @PostMapping("/{id}/regrade")
    public Result<Map<String, Object>> regrade(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(essayService.regrade(id, teacherId));
    }

    @PutMapping("/{id}/final-score")
    public Result<Essay> updateFinalScore(@PathVariable Long id,
                                          @Valid @RequestBody FinalScoreDTO dto,
                                          HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(essayService.updateFinalScore(id, dto.getFinalScore(), teacherId));
    }

    @PutMapping("/{id}/review")
    public Result<Essay> review(@PathVariable Long id,
                                @Valid @RequestBody EssayReviewDTO dto,
                                HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(essayService.reviewEssay(id, dto, teacherId));
    }
}
