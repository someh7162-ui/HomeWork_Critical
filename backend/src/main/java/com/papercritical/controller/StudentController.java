package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.entity.Student;
import com.papercritical.service.AiGradingService;
import com.papercritical.service.OcrService;
import com.papercritical.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    private final StudentService studentService;
    private final OcrService ocrService;
    private final AiGradingService aiGradingService;

    @Value("${upload.path}")
    private String uploadPath;

    public StudentController(StudentService studentService, OcrService ocrService,
                             AiGradingService aiGradingService) {
        this.studentService = studentService;
        this.ocrService = ocrService;
        this.aiGradingService = aiGradingService;
    }

    @PostMapping
    public Result<Student> add(@RequestBody Student student, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(studentService.add(student, teacherId));
    }

    @GetMapping
    public Result<List<Student>> list(@RequestParam(required = false) String className,
                                       HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(studentService.list(teacherId, className));
    }

    @GetMapping("/classes")
    public Result<List<String>> listClasses(HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        return Result.ok(studentService.listClasses(teacherId));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        studentService.delete(id, teacherId);
        return Result.ok();
    }

    @DeleteMapping("/class")
    public Result<Map<String, Object>> deleteClass(@RequestParam String className,
                                                    HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        int count = studentService.deleteByClass(className, teacherId);
        log.info("Deleted class '{}': {} students removed by teacher {}", className, count, teacherId);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("deleted", count);
        return Result.ok(data);
    }

    @PostMapping("/roster-ocr")
    public Result<List<Map<String, String>>> rosterOcr(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("上传文件为空");
        }

        Path tempPath = null;
        try {
            Path basePath = Paths.get(uploadPath);
            if (!basePath.isAbsolute()) {
                basePath = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
            }
            Path tempDir = basePath.resolve("temp");
            Files.createDirectories(tempDir);

            tempPath = tempDir.resolve(UUID.randomUUID() + ".jpg");
            file.transferTo(tempPath.toFile());
            log.info("Roster OCR temp file: {} ({} bytes)", tempPath, tempPath.toFile().length());

            // 1: 视觉模型直接识别（mimo-v2-omni）
            List<Map<String, String>> students = aiGradingService.parseRosterVision(tempPath.toFile());
            if (students != null && !students.isEmpty()) {
                log.info("Roster vision parsed: {} students", students.size());
                return Result.ok(students);
            }

            // 2: OCR + AI 降级
            log.info("Vision return empty, fallback to OCR...");
            String ocrText = ocrService.recognizeRoster(tempPath.toFile());
            log.info("Roster OCR result length: {}", ocrText != null ? ocrText.length() : 0);

            if (!AiGradingService.isOcrTextUsable(ocrText)) {
                return Result.error("未能识别图片中的文字，请确保花名册图片清晰");
            }

            students = aiGradingService.parseRoster(ocrText);

            if (students == null || students.isEmpty()) {
                return Result.error("未能从图片中解析出学生姓名，请确认花名册格式（每行一个姓名）");
            }
            log.info("Roster parsed: {} students", students.size());
            return Result.ok(students);
        } catch (IOException e) {
            log.error("Roster OCR file error", e);
            return Result.error("文件处理失败: " + e.getMessage());
        } finally {
            if (tempPath != null) {
                try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
            }
        }
    }

    @PostMapping("/batch")
    public Result<Map<String, Object>> batchAdd(@RequestBody List<Student> students,
                                                 HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("teacherId");
        if (students == null || students.isEmpty()) {
            return Result.error("学生列表不能为空");
        }
        int count = studentService.batchAdd(students, teacherId);
        log.info("Batch added {} students by teacher {}", count, teacherId);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("count", count);
        return Result.ok(data);
    }
}
