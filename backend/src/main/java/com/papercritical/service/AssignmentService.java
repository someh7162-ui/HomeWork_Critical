package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.dto.AssignmentDTO;
import com.papercritical.entity.Assignment;
import com.papercritical.mapper.AssignmentMapper;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AssignmentService {

    public static final String TYPE_ESSAY = "ESSAY";
    public static final String TYPE_WORKBOOK = "WORKBOOK";
    private static final String ANSWER_PENDING = "PENDING";
    private static final String ANSWER_PARSING = "PARSING";
    private static final String ANSWER_READY = "READY";
    private static final String ANSWER_FAILED = "FAILED";

    private final AssignmentMapper assignmentMapper;
    private final AiGradingService aiGradingService;
    private final MineruService mineruService;
    private final ExecutorService answerKeyExecutor = Executors.newFixedThreadPool(2);

    @Value("${upload.path}")
    private String uploadPath;

    public AssignmentService(AssignmentMapper assignmentMapper, AiGradingService aiGradingService,
                             MineruService mineruService) {
        this.assignmentMapper = assignmentMapper;
        this.aiGradingService = aiGradingService;
        this.mineruService = mineruService;
    }

    public Assignment create(AssignmentDTO dto, Long teacherId) {
        Assignment assignment = new Assignment();
        assignment.setTeacherId(teacherId);
        assignment.setTitle(dto.getTitle());
        String type = normalizeType(dto.getAssignmentType());
        validate(dto, type);
        assignment.setAssignmentType(type);
        assignment.setQuestion(defaultString(dto.getQuestion()));
        assignment.setModelEssay(defaultString(dto.getModelEssay()));
        assignment.setQuestionRange(defaultString(dto.getQuestionRange()));
        assignment.setAnswerKeyStatus(TYPE_WORKBOOK.equals(type) ? ANSWER_PENDING : "");
        assignment.setAnswerKeyMessage(TYPE_WORKBOOK.equals(type) ? "Waiting for answer key images" : "");
        assignment.setWordLimitMin(dto.getWordLimitMin());
        assignment.setWordLimitMax(dto.getWordLimitMax());
        assignment.setTotalScore(dto.getTotalScore());
        assignment.setScoringCriteria(dto.getScoringCriteria());
        assignment.setClassName(dto.getClassName());
        assignment.setSubject(dto.getSubject());
        assignmentMapper.insert(assignment);
        return assignment;
    }

    public List<Assignment> list(Long teacherId, String className) {
        LambdaQueryWrapper<Assignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Assignment::getTeacherId, teacherId);
        if (className != null && !className.trim().isEmpty()) {
            wrapper.eq(Assignment::getClassName, className.trim());
        }
        wrapper.orderByDesc(Assignment::getCreatedAt);
        return assignmentMapper.selectList(wrapper);
    }

    public Assignment detail(Long id, Long teacherId) {
        LambdaQueryWrapper<Assignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Assignment::getId, id).eq(Assignment::getTeacherId, teacherId);
        Assignment assignment = assignmentMapper.selectOne(wrapper);
        if (assignment == null) {
            throw new IllegalArgumentException("作业不存在或无权操作");
        }
        return assignment;
    }

    public boolean delete(Long id, Long teacherId) {
        LambdaQueryWrapper<Assignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Assignment::getId, id).eq(Assignment::getTeacherId, teacherId);
        return assignmentMapper.delete(wrapper) > 0;
    }

    public Assignment update(Long id, AssignmentDTO dto, Long teacherId) {
        LambdaQueryWrapper<Assignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Assignment::getId, id).eq(Assignment::getTeacherId, teacherId);
        Assignment existing = assignmentMapper.selectOne(wrapper);
        if (existing == null) {
            throw new IllegalArgumentException("作业不存在或无权操作");
        }
        existing.setTitle(dto.getTitle());
        String type = normalizeType(dto.getAssignmentType());
        validate(dto, type);
        existing.setAssignmentType(type);
        existing.setQuestion(defaultString(dto.getQuestion()));
        existing.setModelEssay(defaultString(dto.getModelEssay()));
        existing.setQuestionRange(defaultString(dto.getQuestionRange()));
        if (TYPE_WORKBOOK.equals(type) && isBlank(existing.getAnswerKeyStatus())) {
            existing.setAnswerKeyStatus(ANSWER_PENDING);
            existing.setAnswerKeyMessage("Waiting for answer key images");
        }
        existing.setWordLimitMin(dto.getWordLimitMin());
        existing.setWordLimitMax(dto.getWordLimitMax());
        existing.setTotalScore(dto.getTotalScore());
        existing.setScoringCriteria(dto.getScoringCriteria());
        existing.setClassName(dto.getClassName());
        existing.setSubject(dto.getSubject());
        assignmentMapper.updateById(existing);
        return existing;
    }

    public Map<String, Object> uploadAnswerKey(Long assignmentId, Long teacherId, MultipartFile[] files) {
        Assignment assignment = detail(assignmentId, teacherId);
        if (!TYPE_WORKBOOK.equals(normalizeType(assignment.getAssignmentType()))) {
            throw new IllegalArgumentException("Only workbook assignments can upload answer keys");
        }
        List<MultipartFile> validFiles = normalizeFiles(files);
        if (validFiles.isEmpty()) {
            throw new IllegalArgumentException("Please upload at least one answer key image");
        }
        List<String> imageUrls = saveImages(validFiles);
        if (imageUrls.isEmpty()) {
            throw new IllegalArgumentException("Failed to save answer key images");
        }

        Assignment updating = new Assignment();
        updating.setId(assignmentId);
        updating.setAnswerKeyImages(JSONUtil.toJsonStr(imageUrls));
        updating.setAnswerKeyStatus(ANSWER_PARSING);
        updating.setAnswerKeyMessage("Parsing answer key images");
        updating.setAnswerKeyJson("");
        updating.setAnswerKeyMineruMarkdown("");
        updating.setAnswerKeyMineruStructureJson("");
        assignmentMapper.updateById(updating);

        answerKeyExecutor.submit(() -> parseAnswerKeyAsync(assignmentId, imageUrls));
        return answerKeyStatus(assignmentId, teacherId);
    }

    public Map<String, Object> answerKeyStatus(Long assignmentId, Long teacherId) {
        Assignment assignment = detail(assignmentId, teacherId);
        Map<String, Object> row = new HashMap<>();
        row.put("assignmentId", assignment.getId());
        row.put("status", emptyAs(assignment.getAnswerKeyStatus(), ANSWER_PENDING));
        row.put("message", assignment.getAnswerKeyMessage());
        row.put("images", parseJsonArray(assignment.getAnswerKeyImages()));
        row.put("answerKey", parseJsonObject(assignment.getAnswerKeyJson()));
        return row;
    }

    public Map<String, Object> updateAnswerKey(Long assignmentId, Long teacherId, Map<String, Object> answerKey) {
        Assignment assignment = detail(assignmentId, teacherId);
        if (!TYPE_WORKBOOK.equals(normalizeType(assignment.getAssignmentType()))) {
            throw new IllegalArgumentException("Only workbook assignments can update answer keys");
        }
        if (answerKey == null || !(answerKey.get("questions") instanceof List)
                || ((List<?>) answerKey.get("questions")).isEmpty()) {
            throw new IllegalArgumentException("Answer key questions cannot be empty");
        }
        Assignment updating = new Assignment();
        updating.setId(assignmentId);
        updating.setAnswerKeyJson(JSONUtil.toJsonStr(answerKey));
        updating.setAnswerKeyStatus(ANSWER_READY);
        updating.setAnswerKeyMessage("Answer key checked and saved by teacher");
        assignmentMapper.updateById(updating);
        return answerKeyStatus(assignmentId, teacherId);
    }

    private void parseAnswerKeyAsync(Long assignmentId, List<String> imageUrls) {
        try {
            Assignment assignment = assignmentMapper.selectById(assignmentId);
            if (assignment == null) {
                return;
            }
            List<File> imageFiles = new java.util.ArrayList<>();
            for (String imageUrl : imageUrls) {
                imageFiles.add(resolveImagePath(imageUrl).toFile());
            }
            List<MineruService.ParseResult> parseResults = new java.util.ArrayList<>();
            for (File imageFile : imageFiles) {
                MineruService.ParseResult pageResult = mineruService.parse(imageFile);
                if (pageResult == null || !pageResult.hasContent()) {
                    updateAnswerKeyStatus(assignmentId, ANSWER_FAILED,
                            answerKeyParseFailureMessage(mineruService.getLastError()), "");
                    return;
                }
                parseResults.add(pageResult);
            }
            MineruService.ParseResult mergedParseResult = mineruService.mergeResults(parseResults);
            String answerKeyText = mergedParseResult.toAnalysisText();
            if (isBlank(answerKeyText)) {
                updateAnswerKeyStatus(assignmentId, ANSWER_FAILED,
                        "答案页解析未返回有效文本，请确认图片中包含清晰、完整的题号和答案。", "");
                return;
            }
            updateAnswerKeyMineruResult(assignmentId, mergedParseResult);
            String answerKeyJson = aiGradingService.parseWorkbookAnswerKeyFromStructure(assignment, answerKeyText);
            if (isBlank(answerKeyJson)) {
                updateAnswerKeyStatus(assignmentId, ANSWER_FAILED,
                        "答案结构生成失败，请检查答案页是否包含题号、答案和必要解析。", "");
                return;
            }
            updateAnswerKeyStatus(assignmentId, ANSWER_READY, "答案页解析完成", answerKeyJson);
        } catch (Exception e) {
            updateAnswerKeyStatus(assignmentId, ANSWER_FAILED, "Answer key parsing error: " + e.getMessage(), "");
        }
    }

    private void updateAnswerKeyStatus(Long assignmentId, String status, String message, String json) {
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setAnswerKeyStatus(status);
        assignment.setAnswerKeyMessage(message);
        assignment.setAnswerKeyJson(json == null ? "" : json);
        assignmentMapper.updateById(assignment);
    }

    private String answerKeyParseFailureMessage(String mineruError) {
        if (isBlank(mineruError)) {
            return "答案页解析失败，请确认图片中包含清晰、完整的题号和答案。";
        }
        String lower = mineruError.toLowerCase();
        if (lower.contains("api key")) {
            return "答案页解析服务未配置 API Key，请联系管理员检查服务器配置。";
        }
        if (lower.contains("version") && (lower.contains("invalid") || lower.contains("not supported"))) {
            return "答案页解析服务的模型版本配置不受支持，请联系管理员检查 MinerU 配置。";
        }
        if (lower.contains("timed out")) {
            return "答案页解析服务处理超时，请稍后重试或减少单次上传页数。";
        }
        if (lower.contains("upload")) {
            return "答案页上传到解析服务失败，请稍后重试。";
        }
        return "答案页解析失败：" + mineruError;
    }

    private void updateAnswerKeyMineruResult(Long assignmentId, MineruService.ParseResult parseResult) {
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setAnswerKeyMineruMarkdown(parseResult == null ? "" : parseResult.getMarkdown());
        assignment.setAnswerKeyMineruStructureJson(parseResult == null ? "" : parseResult.getStructureJson());
        assignmentMapper.updateById(assignment);
    }

    private void validate(AssignmentDTO dto, String type) {
        if (TYPE_WORKBOOK.equals(type)) {
            if (isBlank(dto.getQuestionRange())) {
                throw new IllegalArgumentException("Workbook assignments require a question range");
            }
            return;
        }
        if (isBlank(dto.getQuestion())) {
            throw new IllegalArgumentException("Essay prompt cannot be empty");
        }
        if (isBlank(dto.getModelEssay())) {
            throw new IllegalArgumentException("Essay reference answer cannot be empty");
        }
    }

    private String normalizeType(String type) {
        return TYPE_WORKBOOK.equalsIgnoreCase(type) ? TYPE_WORKBOOK : TYPE_ESSAY;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String emptyAs(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<MultipartFile> normalizeFiles(MultipartFile[] files) {
        List<MultipartFile> normalized = new java.util.ArrayList<>();
        if (files == null) {
            return normalized;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                normalized.add(file);
            }
        }
        return normalized;
    }

    private List<String> saveImages(List<MultipartFile> files) {
        List<String> imageUrls = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            try {
                String imageUrl = saveImage(file.getBytes());
                if (imageUrl != null) {
                    imageUrls.add(imageUrl);
                }
            } catch (IOException ignored) {
            }
        }
        return imageUrls;
    }

    private String saveImage(byte[] imageBytes) {
        try {
            Path basePath = Paths.get(uploadPath);
            if (!basePath.isAbsolute()) {
                basePath = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
            }
            String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String filename = datePath + "/" + UUID.randomUUID() + ".jpg";
            Path dest = basePath.resolve(filename);
            Files.createDirectories(dest.getParent());
            Files.write(dest, imageBytes);
            return filename;
        } catch (IOException e) {
            return null;
        }
    }

    private Path resolveImagePath(String imageUrl) {
        Path imagePath = Paths.get(uploadPath);
        if (!imagePath.isAbsolute()) {
            imagePath = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
        }
        return imagePath.resolve(imageUrl);
    }

    private Object parseJsonArray(String json) {
        if (isBlank(json)) {
            return java.util.Collections.emptyList();
        }
        try {
            return JSONUtil.parseArray(json);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private Object parseJsonObject(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return json;
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        answerKeyExecutor.shutdown();
    }
}
