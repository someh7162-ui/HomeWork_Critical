package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.dto.AdminLoginDTO;
import com.papercritical.dto.QuotaSummaryDTO;
import com.papercritical.entity.QuotaCard;
import com.papercritical.service.AdminService;
import com.papercritical.service.QuotaCardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final QuotaCardService cardService;

    public AdminController(AdminService adminService, QuotaCardService cardService) {
        this.adminService = adminService;
        this.cardService = cardService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody(required = false) AdminLoginDTO dto) {
        return Result.ok(adminService.login(dto));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(adminService.dashboard());
    }

    @GetMapping("/device")
    public Result<Map<String, Object>> deviceStatus() {
        return Result.ok(adminService.deviceStatus());
    }

    @GetMapping("/teachers")
    public Result<List<Map<String, Object>>> teachers() {
        return Result.ok(adminService.teachers());
    }

    @PostMapping("/teachers/{teacherId}/quota")
    public Result<QuotaSummaryDTO> grantQuota(@PathVariable Long teacherId, @RequestBody Map<String, Integer> body) {
        return Result.ok(adminService.grantQuota(teacherId, body == null ? null : body.get("amount")));
    }

    @GetMapping("/essays")
    public Result<List<Map<String, Object>>> essays() {
        return Result.ok(adminService.essays());
    }

    @PostMapping("/cards/generate")
    public Result<Map<String, Object>> generateCards(@RequestBody Map<String, Object> body) {
        int amount = Integer.parseInt(String.valueOf(body.getOrDefault("amount", 500)));
        int count = Integer.parseInt(String.valueOf(body.getOrDefault("count", 1)));
        return Result.ok(cardService.generateCards(amount, count));
    }

    @GetMapping("/cards")
    public Result<List<QuotaCard>> listCards(@RequestParam(required = false) String status) {
        return Result.ok(cardService.listCards(status));
    }
}
