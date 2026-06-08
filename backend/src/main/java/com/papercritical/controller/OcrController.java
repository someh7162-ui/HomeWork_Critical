package com.papercritical.controller;

import com.papercritical.common.Result;
import com.papercritical.service.OcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    private final OcrService ocrService;

    @Value("${upload.path}")
    private String uploadPath;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognize(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("上传文件为空");
        }

        Path tempPath = null;
        try {
            // 解析为绝对路径，避免相对路径问题
            Path basePath = Paths.get(uploadPath);
            if (!basePath.isAbsolute()) {
                basePath = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
            }
            Path tempDir = basePath.resolve("temp");
            Files.createDirectories(tempDir);

            tempPath = tempDir.resolve(UUID.randomUUID() + ".jpg");
            file.transferTo(tempPath.toFile());
            log.info("OCR temp file saved: {}", tempPath);

            String text = ocrService.recognize(tempPath.toFile());
            if (text == null || text.trim().isEmpty()) {
                return Result.error("OCR识别失败，请确认图片清晰度");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("text", text);
            return Result.ok(data);
        } catch (IOException e) {
            log.error("OCR file processing error, path={}", tempPath, e);
            return Result.error("文件处理失败: " + e.getMessage());
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }
}
