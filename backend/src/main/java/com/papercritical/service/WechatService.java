package com.papercritical.service;

import com.papercritical.entity.Teacher;
import com.papercritical.mapper.TeacherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
public class WechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatService.class);

    private final TeacherMapper teacherMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.secret}")
    private String appSecret;

    public WechatService(TeacherMapper teacherMapper, PasswordEncoder passwordEncoder) {
        this.teacherMapper = teacherMapper;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 微信登录：code → openid → 查找/创建教师 → 返回教师对象
     */
    public Teacher loginByCode(String code) {
        String openid = code2session(code);
        if (openid == null) {
            return null;
        }

        Teacher teacher = teacherMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Teacher>()
                        .eq(Teacher::getOpenid, openid));

        if (teacher == null) {
            teacher = new Teacher();
            teacher.setOpenid(openid);
            teacher.setUsername("wx_" + UUID.randomUUID().toString().substring(0, 8));
            teacher.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            teacher.setName("微信用户");
            teacher.setSubject("英语");
            teacherMapper.insert(teacher);
            log.info("Created new teacher account for openid: {}", openid);
        }

        return teacher;
    }

    /**
     * 绑定微信到已有账号
     */
    public boolean bindOpenid(Long teacherId, String code) {
        String openid = code2session(code);
        if (openid == null) {
            return false;
        }

        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            return false;
        }
        teacher.setOpenid(openid);
        teacherMapper.updateById(teacher);
        log.info("Bound openid {} to teacher {}", openid, teacherId);
        return true;
    }

    private String code2session(String code) {
        try {
            String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appId, appSecret, code);
            log.info("Calling code2session, code length={}", code != null ? code.length() : 0);

            String body = restTemplate.getForObject(url, String.class);

            if (body == null || body.isEmpty()) {
                log.error("WeChat code2session returned empty body");
                return null;
            }

            Map<String, Object> resp = objectMapper.readValue(body, Map.class);

            if (resp.get("openid") != null) {
                log.info("code2session success, openid={}", resp.get("openid"));
                return (String) resp.get("openid");
            }

            log.error("WeChat code2session failed: errcode={}, errmsg={}", resp.get("errcode"), resp.get("errmsg"));
            return null;
        } catch (Exception e) {
            log.error("WeChat code2session exception", e);
            return null;
        }
    }
}
