package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.common.JwtUtil;
import com.papercritical.common.Result;
import com.papercritical.dto.LoginDTO;
import com.papercritical.dto.QuotaSummaryDTO;
import com.papercritical.dto.RegisterDTO;
import com.papercritical.dto.StudentLoginDTO;
import com.papercritical.entity.Student;
import com.papercritical.entity.Teacher;
import com.papercritical.mapper.StudentMapper;
import com.papercritical.mapper.TeacherMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final JwtUtil jwtUtil;
    private final TeacherQuotaService teacherQuotaService;
    private final WechatService wechatService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(TeacherMapper teacherMapper, StudentMapper studentMapper, JwtUtil jwtUtil,
                        TeacherQuotaService teacherQuotaService,
                        WechatService wechatService,
                        PasswordEncoder passwordEncoder) {
        this.teacherMapper = teacherMapper;
        this.studentMapper = studentMapper;
        this.jwtUtil = jwtUtil;
        this.teacherQuotaService = teacherQuotaService;
        this.wechatService = wechatService;
        this.passwordEncoder = passwordEncoder;
    }

    public Result<Map<String, Object>> login(LoginDTO dto) {
        if (dto == null || dto.getUsername() == null || dto.getUsername().trim().isEmpty()
                || dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getUsername, dto.getUsername().trim());
        Teacher teacher = teacherMapper.selectOne(wrapper);

        if (teacher == null || !passwordEncoder.matches(dto.getPassword(), teacher.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        return buildLoginResult(teacher);
    }

    @Transactional
    public Result<Map<String, Object>> register(RegisterDTO dto) {
        if (dto == null || isBlank(dto.getUsername()) || isBlank(dto.getPassword()) || isBlank(dto.getName())) {
            return Result.error(400, "请填写用户名、密码和教师姓名");
        }
        String username = dto.getUsername().trim();
        if (username.length() < 3 || username.length() > 30) {
            return Result.error(400, "用户名长度需为 3-30 个字符");
        }
        if (!username.matches("^[A-Za-z0-9_]+$")) {
            return Result.error(400, "用户名只能包含字母、数字和下划线");
        }
        if (dto.getPassword().length() < 6 || dto.getPassword().length() > 32) {
            return Result.error(400, "密码长度需为 6-32 位");
        }

        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getUsername, username).last("LIMIT 1");
        if (teacherMapper.selectOne(wrapper) != null) {
            return Result.error(409, "用户名已被注册");
        }

        Teacher teacher = new Teacher();
        teacher.setUsername(username);
        teacher.setPassword(passwordEncoder.encode(dto.getPassword()));
        teacher.setName(dto.getName().trim());
        teacher.setSubject(isBlank(dto.getSubject()) ? "英语" : dto.getSubject().trim());
        teacherMapper.insert(teacher);
        return buildLoginResult(teacher);
    }

    public Result<Map<String, Object>> wechatLogin(String code) {
        Teacher teacher = wechatService.loginByCode(code);
        if (teacher == null) {
            return Result.error(500, "微信登录失败，请稍后重试");
        }
        return buildLoginResult(teacher);
    }

    public Result<Map<String, Object>> studentLogin(StudentLoginDTO dto) {
        if (dto == null || isBlank(dto.getUsername()) || isBlank(dto.getPassword())) {
            return Result.error(400, "账号和密码不能为空");
        }

        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getUsername, dto.getUsername().trim()).last("LIMIT 1");
        Student student = studentMapper.selectOne(wrapper);
        if (student == null || !passwordEncoder.matches(dto.getPassword(), student.getPassword())) {
            return Result.error(401, "学生账号或密码错误");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generateStudentToken(student.getId(), student.getName()));
        data.put("role", "STUDENT");
        data.put("studentId", student.getId());
        data.put("teacherId", student.getTeacherId());
        data.put("name", student.getName());
        data.put("className", student.getClassName());
        data.put("studentNo", student.getStudentNo());
        return Result.ok(data);
    }

    private Result<Map<String, Object>> buildLoginResult(Teacher teacher) {
        String token = jwtUtil.generateToken(teacher.getId(), teacher.getUsername());
        Map<String, Object> data = new HashMap<>();
        QuotaSummaryDTO quotaSummary = teacherQuotaService.getQuotaSummary(teacher.getId());
        data.put("token", token);
        data.put("teacherId", teacher.getId());
        data.put("name", teacher.getName());
        data.put("subject", teacher.getSubject());
        data.put("quotaSummary", quotaSummary);

        return Result.ok(data);
    }

    public Result<Map<String, Object>> updateProfile(Long teacherId, Map<String, String> body) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getId, teacherId);
        Teacher teacher = teacherMapper.selectOne(wrapper);
        if (teacher == null) return Result.error(404, "用户不存在");
        String newSubject = body.get("subject");
        if (newSubject != null && !newSubject.trim().isEmpty()) teacher.setSubject(newSubject.trim());
        String newName = body.get("name");
        if (newName != null && !newName.trim().isEmpty()) teacher.setName(newName.trim());
        teacherMapper.updateById(teacher);
        Teacher updated = teacherMapper.selectOne(wrapper);
        return buildLoginResult(updated);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
