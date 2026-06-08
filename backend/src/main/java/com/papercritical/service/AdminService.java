package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.common.JwtUtil;
import com.papercritical.dto.AdminLoginDTO;
import com.papercritical.dto.QuotaSummaryDTO;
import com.papercritical.entity.Assignment;
import com.papercritical.entity.Essay;
import com.papercritical.entity.Student;
import com.papercritical.entity.Teacher;
import com.papercritical.mapper.AssignmentMapper;
import com.papercritical.mapper.EssayMapper;
import com.papercritical.mapper.StudentMapper;
import com.papercritical.mapper.TeacherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final AssignmentMapper assignmentMapper;
    private final EssayMapper essayMapper;
    private final TeacherQuotaService teacherQuotaService;
    private final JwtUtil jwtUtil;

    @Value("${server.port:8099}")
    private String serverPort;

    @Value("${upload.path:uploads/essays}")
    private String uploadPath;

    @Value("${ocr.app-id:}")
    private String ocrAppId;

    @Value("${ocr.app-secret:}")
    private String ocrAppSecret;

    @Value("${ai.mimo.api-key:}")
    private String mimoApiKey;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${admin.password-hash:$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW}")
    private String adminPasswordHash;

    private final PasswordEncoder passwordEncoder;

    @javax.annotation.PostConstruct
    public void init() {
        log.info("Admin password hash for 'admin123': {}", passwordEncoder.encode("admin123"));
    }

    public AdminService(TeacherMapper teacherMapper, StudentMapper studentMapper,
                        AssignmentMapper assignmentMapper, EssayMapper essayMapper,
                        TeacherQuotaService teacherQuotaService, JwtUtil jwtUtil,
                        PasswordEncoder passwordEncoder) {
        this.teacherMapper = teacherMapper;
        this.studentMapper = studentMapper;
        this.assignmentMapper = assignmentMapper;
        this.essayMapper = essayMapper;
        this.teacherQuotaService = teacherQuotaService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> login(AdminLoginDTO dto) {
        String username = dto == null || dto.getUsername() == null ? "" : dto.getUsername().trim();
        String password = dto == null || dto.getPassword() == null ? "" : dto.getPassword();
        if (!"admin".equals(username) || !passwordEncoder.matches(password, adminPasswordHash)) {
            throw new IllegalArgumentException("管理员账号或密码错误");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generateToken(0L, "admin"));
        data.put("name", "系统管理员");
        return data;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new HashMap<>();
        long teacherCount = teacherMapper.selectCount(null);
        long studentCount = studentMapper.selectCount(null);
        long assignmentCount = assignmentMapper.selectCount(null);
        long essayCount = essayMapper.selectCount(null);

        LambdaQueryWrapper<Essay> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(Essay::getCreatedAt, LocalDate.now().atStartOfDay());
        long todayEssayCount = essayMapper.selectCount(todayWrapper);

        LambdaQueryWrapper<Essay> failedWrapper = new LambdaQueryWrapper<>();
        failedWrapper.eq(Essay::getStatus, "FAILED");
        long failedEssayCount = essayMapper.selectCount(failedWrapper);

        data.put("teacherCount", teacherCount);
        data.put("studentCount", studentCount);
        data.put("assignmentCount", assignmentCount);
        data.put("essayCount", essayCount);
        data.put("todayEssayCount", todayEssayCount);
        data.put("failedEssayCount", failedEssayCount);
        data.put("successRate", essayCount == 0 ? 100 : Math.round((essayCount - failedEssayCount) * 10000.0 / essayCount) / 100.0);
        data.put("recentEssays", recentEssays(8));
        return data;
    }

    public Map<String, Object> deviceStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("backendStatus", "UP");
        data.put("serverPort", serverPort);
        data.put("localIp", localIp());
        data.put("candidateIps", candidateIps());
        data.put("appServerUrl", "http://" + localIp() + ":" + serverPort);

        File uploadDir = resolveUploadDir();
        data.put("uploadPath", uploadDir.getAbsolutePath());
        data.put("uploadExists", uploadDir.exists());
        data.put("uploadWritable", uploadDir.exists() ? uploadDir.canWrite() : uploadDir.mkdirs());
        data.put("ocrConfigured", !isBlank(ocrAppId) && !isBlank(ocrAppSecret));
        data.put("aiConfigured", !isBlank(mimoApiKey));
        data.put("checkedAt", LocalDateTime.now().toString());
        return data;
    }

    public List<Map<String, Object>> teachers() {
        List<Teacher> teachers = teacherMapper.selectList(new LambdaQueryWrapper<Teacher>().orderByDesc(Teacher::getCreatedAt));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Teacher teacher : teachers) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", teacher.getId());
            row.put("username", teacher.getUsername());
            row.put("name", teacher.getName());
            row.put("subject", teacher.getSubject());
            row.put("createdAt", teacher.getCreatedAt());
            row.put("studentCount", countStudents(teacher.getId()));
            row.put("assignmentCount", countAssignments(teacher.getId()));
            row.put("essayCount", countEssaysByTeacher(teacher.getId()));
            row.put("quotaSummary", teacherQuotaService.getQuotaSummary(teacher.getId()));
            list.add(row);
        }
        return list;
    }

    public QuotaSummaryDTO grantQuota(Long teacherId, Integer amount) {
        if (teacherId == null || amount == null || amount <= 0) {
            throw new IllegalArgumentException("额度数量必须大于0");
        }
        return teacherQuotaService.grantPaidQuota(teacherId, "ADMIN-GRANT-" + System.currentTimeMillis(), amount, "后台手动增加额度");
    }

    public List<Map<String, Object>> essays() {
        return recentEssays(50);
    }

    private List<Map<String, Object>> recentEssays(int limit) {
        List<Essay> essays = essayMapper.selectList(new LambdaQueryWrapper<Essay>()
                .orderByDesc(Essay::getCreatedAt)
                .last("LIMIT " + limit));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Essay essay : essays) {
            Student student = studentMapper.selectById(essay.getStudentId());
            Assignment assignment = assignmentMapper.selectById(essay.getAssignmentId());
            Teacher teacher = assignment == null ? null : teacherMapper.selectById(assignment.getTeacherId());

            Map<String, Object> row = new HashMap<>();
            row.put("id", essay.getId());
            row.put("studentName", resolveStudentName(student, essay.getStudentId()));
            row.put("studentMissing", student == null);
            row.put("className", student == null ? "" : student.getClassName());
            row.put("teacherName", teacher == null ? "" : teacher.getName());
            row.put("assignmentTitle", assignment == null ? "作业#" + essay.getAssignmentId() : assignment.getTitle());
            row.put("aiScore", essay.getAiScore());
            row.put("finalScore", essay.getFinalScore());
            row.put("status", essay.getStatus());
            row.put("imageUrl", essay.getImageUrl());
            row.put("ocrText", essay.getOcrText());
            row.put("aiFeedback", essay.getAiFeedback());
            row.put("createdAt", essay.getCreatedAt());
            row.put("studentId", essay.getStudentId());
            list.add(row);
        }
        return list;
    }

    private String resolveStudentName(Student student, Long studentId) {
        if (student != null && !isBlank(student.getName())) {
            return student.getName();
        }
        return "学生记录已删除或不存在（ID: " + studentId + "）";
    }

    private long countStudents(Long teacherId) {
        return studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getTeacherId, teacherId));
    }

    private long countAssignments(Long teacherId) {
        return assignmentMapper.selectCount(new LambdaQueryWrapper<Assignment>().eq(Assignment::getTeacherId, teacherId));
    }

    private long countEssaysByTeacher(Long teacherId) {
        List<Assignment> assignments = assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>().eq(Assignment::getTeacherId, teacherId));
        if (assignments.isEmpty()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>();
        for (Assignment assignment : assignments) {
            ids.add(assignment.getId());
        }
        return essayMapper.selectCount(new LambdaQueryWrapper<Essay>().in(Essay::getAssignmentId, ids));
    }

    private File resolveUploadDir() {
        File dir = new File(uploadPath);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), uploadPath);
        }
        return dir;
    }

    private String localIp() {
        List<String> ips = candidateIps();
        if (!ips.isEmpty()) {
            return ips.get(0);
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private List<String> candidateIps() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    String host = address.getHostAddress();
                    if (host.contains(":")) {
                        continue;
                    }
                    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                        ips.add(host);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ips;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
