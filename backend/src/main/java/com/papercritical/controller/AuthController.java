package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.dto.LoginDTO;
import com.papercritical.dto.RegisterDTO;
import com.papercritical.dto.StudentLoginDTO;
import com.papercritical.dto.WechatLoginDTO;
import com.papercritical.service.AuthService;
import com.papercritical.service.QuotaCardService;
import cn.hutool.json.JSONUtil;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final QuotaCardService cardService;

    public AuthController(AuthService authService, QuotaCardService cardService) {
        this.authService = authService;
        this.cardService = cardService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody(required = false) String body) {
        LoginDTO dto = new LoginDTO();
        if (body != null && !body.trim().isEmpty()) {
            String value = body.trim();
            if (value.startsWith("{")) {
                dto = JSONUtil.toBean(value, LoginDTO.class);
            } else {
                Map<String, String> params = parseFormBody(value);
                dto.setUsername(params.get("username"));
                dto.setPassword(params.get("password"));
            }
        }
        return authService.login(dto);
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO dto) {
        return authService.register(dto);
    }

    @PostMapping("/wechat-login")
    public Result<Map<String, Object>> wechatLogin(@RequestBody WechatLoginDTO dto) {
        return authService.wechatLogin(dto.getCode());
    }

    @PostMapping("/student-login")
    public Result<Map<String, Object>> studentLogin(@RequestBody StudentLoginDTO dto) {
        return authService.studentLogin(dto);
    }

    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body,
                                                      HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return authService.updateProfile(teacherId, body);
    }

    @PostMapping("/redeem")
    public Result<Map<String, Object>> redeem(@RequestBody Map<String, String> body,
                                              HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        try {
            return Result.ok(cardService.redeem(body.get("cardKey"), teacherId));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private Map<String, String> parseFormBody(String body) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}
