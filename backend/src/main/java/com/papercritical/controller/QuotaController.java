package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.dto.QuotaSummaryDTO;
import com.papercritical.service.TeacherQuotaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final TeacherQuotaService teacherQuotaService;

    public QuotaController(TeacherQuotaService teacherQuotaService) {
        this.teacherQuotaService = teacherQuotaService;
    }

    @GetMapping("/me")
    public Result<QuotaSummaryDTO> me(HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(teacherQuotaService.getQuotaSummary(teacherId));
    }
}
