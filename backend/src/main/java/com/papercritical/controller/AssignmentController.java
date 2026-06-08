package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.dto.AssignmentDTO;
import com.papercritical.entity.Assignment;
import com.papercritical.service.AssignmentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    public Result<Assignment> create(@Valid @RequestBody AssignmentDTO dto, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentService.create(dto, teacherId));
    }

    @GetMapping
    public Result<List<Assignment>> list(@RequestParam(required = false) String className,
                                          HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentService.list(teacherId, className));
    }

    @GetMapping("/{id}")
    public Result<Assignment> detail(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentService.detail(id, teacherId));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        assignmentService.delete(id, teacherId);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Assignment> update(@PathVariable Long id, @Valid @RequestBody AssignmentDTO dto,
                                      HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentService.update(id, dto, teacherId));
    }

    @PostMapping("/{id}/answer-key")
    public Result<?> uploadAnswerKey(@PathVariable Long id,
                                     @RequestParam("files") MultipartFile[] files,
                                     HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentService.uploadAnswerKey(id, teacherId, files));
    }

    @GetMapping("/{id}/answer-key")
    public Result<?> answerKey(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentService.answerKeyStatus(id, teacherId));
    }

    @PutMapping("/{id}/answer-key")
    public Result<?> updateAnswerKey(@PathVariable Long id,
                                     @RequestBody Map<String, Object> answerKey,
                                     HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(assignmentService.updateAnswerKey(id, teacherId, answerKey));
    }
}
