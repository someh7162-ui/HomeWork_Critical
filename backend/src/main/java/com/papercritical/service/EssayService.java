package com.papercritical.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.papercritical.dto.EssayReviewDTO;
import com.papercritical.dto.GradingResultDTO;
import com.papercritical.dto.QuotaSummaryDTO;
import com.papercritical.entity.Assignment;
import com.papercritical.entity.Essay;
import com.papercritical.entity.Student;
import com.papercritical.mapper.AssignmentMapper;
import com.papercritical.mapper.EssayMapper;
import com.papercritical.mapper.StudentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EssayService {

    private static final Logger log = LoggerFactory.getLogger(EssayService.class);

    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_OCR_PROCESSING = "OCR_PROCESSING";
    private static final String STATUS_OCR_DONE = "OCR_DONE";
    private static final String STATUS_AI_PROCESSING = "AI_PROCESSING";
    private static final String STATUS_GRADED = "GRADED";
    private static final String STATUS_TEACHER_REVIEWED = "TEACHER_REVIEWED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TYPE_WORKBOOK = "WORKBOOK";
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.7800");
    private static final BigDecimal REVIEW_CONFIDENCE_THRESHOLD = new BigDecimal("0.7500");

    private final EssayMapper essayMapper;
    private final AssignmentMapper assignmentMapper;
    private final StudentMapper studentMapper;
    private final OcrService ocrService;
    private final MineruService mineruService;
    private final AiGradingService aiGradingService;
    private final TeacherQuotaService teacherQuotaService;
    private final AnnotationImageService annotationImageService;
    private final ExecutorService gradingExecutor = Executors.newFixedThreadPool(3);

    @Value("${upload.path}")
    private String uploadPath;

    public EssayService(EssayMapper essayMapper, AssignmentMapper assignmentMapper,
                        StudentMapper studentMapper, OcrService ocrService,
                        MineruService mineruService,
                        AiGradingService aiGradingService,
                        TeacherQuotaService teacherQuotaService,
                        AnnotationImageService annotationImageService) {
        this.essayMapper = essayMapper;
        this.assignmentMapper = assignmentMapper;
        this.studentMapper = studentMapper;
        this.ocrService = ocrService;
        this.mineruService = mineruService;
        this.aiGradingService = aiGradingService;
        this.teacherQuotaService = teacherQuotaService;
        this.annotationImageService = annotationImageService;
    }

    /**
     * 上传图片后只创建批改任务；OCR/AI 在后台执行，前端通过进度接口轮询。
     */
    public Map<String, Object> uploadAndGrade(MultipartFile file, Long assignmentId, Long studentId, Long teacherId) {
        Map<String, Object> result = new HashMap<>();

        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !teacherId.equals(assignment.getTeacherId())) {
            result.put("success", false);
            result.put("message", "作业不存在或无权操作");
            return result;
        }

        Student student = studentMapper.selectById(studentId);
        if (student == null || !teacherId.equals(student.getTeacherId())) {
            result.put("success", false);
            result.put("message", "学生不存在或无权操作");
            return result;
        }
        if (!assignment.getTeacherId().equals(student.getTeacherId())) {
            result.put("success", false);
            result.put("message", "学生与作业不属于同一教师");
            return result;
        }

        byte[] imageBytes;
        String contentHash;
        try {
            imageBytes = file.getBytes();
            contentHash = DigestUtil.sha256Hex(imageBytes);
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes", e);
            result.put("success", false);
            result.put("message", "读取图片失败");
            return result;
        }

        Essay existingEssay = findExistingEssay(assignmentId, studentId, contentHash);
        if (existingEssay != null) {
            result.put("success", true);
            result.put("essayId", existingEssay.getId());
            result.put("status", existingEssay.getStatus());
            result.put("progressPercent", 100);
            result.put("ocrText", existingEssay.getOcrText());
            result.put("gradingResult", parseStoredGradingResult(existingEssay));
            result.put("quotaSummary", teacherQuotaService.getQuotaSummary(assignment.getTeacherId()));
            result.put("reused", true);
            return result;
        }

        TeacherQuotaService.ConsumeResult consumeResult = teacherQuotaService.consumeOneQuota(
                assignment.getTeacherId(), assignmentId, studentId, "作文首次批改预扣额度");

        String imageUrl = saveImage(imageBytes);
        if (imageUrl == null) {
            teacherQuotaService.refundQuota(assignment.getTeacherId(), consumeResult,
                    assignmentId, studentId, "图片保存失败，返还额度");
            result.put("success", false);
            result.put("message", "图片保存失败");
            return result;
        }

        Essay essay = new Essay();
        essay.setAssignmentId(assignmentId);
        essay.setStudentId(studentId);
        essay.setContentHash(contentHash);
        essay.setImageUrl(imageUrl);
        essay.setTeacherId(assignment.getTeacherId());
        essay.setStatus(STATUS_UPLOADED);
        essay.setGradingStartedAt(LocalDateTime.now());
        essayMapper.insert(essay);

        gradingExecutor.submit(() -> processEssayAsync(
                essay.getId(), assignmentId, studentId, assignment.getTeacherId(), consumeResult));

        result.put("success", true);
        result.put("essayId", essay.getId());
        result.put("status", STATUS_UPLOADED);
        result.put("progressPercent", 10);
        result.put("message", "作文已上传，正在排队批改");
        result.put("quotaSummary", consumeResult.getQuotaSummary());
        result.put("reused", false);
        return result;
    }

    public Map<String, Object> uploadAndGradeWorkbook(MultipartFile[] files, Long assignmentId, Long studentId, Long teacherId) {
        Map<String, Object> result = new HashMap<>();

        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !teacherId.equals(assignment.getTeacherId())) {
            result.put("success", false);
            result.put("message", "Assignment not found or forbidden");
            return result;
        }
        if (!TYPE_WORKBOOK.equalsIgnoreCase(assignment.getAssignmentType())) {
            result.put("success", false);
            result.put("message", "This assignment is not a workbook assignment");
            return result;
        }
        if (!"READY".equals(assignment.getAnswerKeyStatus()) || isBlank(assignment.getAnswerKeyJson())) {
            result.put("success", false);
            result.put("message", "Answer key is not ready yet");
            return result;
        }

        Student student = studentMapper.selectById(studentId);
        if (student == null || !teacherId.equals(student.getTeacherId())) {
            result.put("success", false);
            result.put("message", "Student not found or forbidden");
            return result;
        }

        List<byte[]> imageBytesList = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                try {
                    imageBytesList.add(file.getBytes());
                } catch (IOException e) {
                    log.error("Failed to read workbook image", e);
                }
            }
        }
        if (imageBytesList.isEmpty()) {
            result.put("success", false);
            result.put("message", "Please upload at least one workbook image");
            return result;
        }

        String contentHash = hashImages(imageBytesList);
        Essay existingEssay = findExistingEssay(assignmentId, studentId, contentHash);
        if (existingEssay != null) {
            result.put("success", true);
            result.put("essayId", existingEssay.getId());
            result.put("status", existingEssay.getStatus());
            result.put("progressPercent", 100);
            result.put("workbookResult", parseWorkbookResult(existingEssay.getWorkbookResultJson()));
            result.put("quotaSummary", teacherQuotaService.getQuotaSummary(assignment.getTeacherId()));
            result.put("reused", true);
            return result;
        }

        TeacherQuotaService.ConsumeResult consumeResult = teacherQuotaService.consumeOneQuota(
                assignment.getTeacherId(), assignmentId, studentId, "workbook grading quota");

        List<String> imageUrls = saveImages(imageBytesList);
        if (imageUrls.isEmpty()) {
            teacherQuotaService.refundQuota(assignment.getTeacherId(), consumeResult,
                    assignmentId, studentId, "workbook image save failed");
            result.put("success", false);
            result.put("message", "Failed to save workbook images");
            return result;
        }

        Essay essay = new Essay();
        essay.setAssignmentId(assignmentId);
        essay.setStudentId(studentId);
        essay.setContentHash(contentHash);
        essay.setImageUrl(imageUrls.get(0));
        essay.setImageUrls(JSONUtil.toJsonStr(imageUrls));
        essay.setTeacherId(assignment.getTeacherId());
        essay.setStatus(STATUS_UPLOADED);
        essay.setGradingStartedAt(LocalDateTime.now());
        essayMapper.insert(essay);

        gradingExecutor.submit(() -> processWorkbookAsync(
                essay.getId(), assignmentId, studentId, assignment.getTeacherId(), consumeResult));

        result.put("success", true);
        result.put("essayId", essay.getId());
        result.put("status", STATUS_UPLOADED);
        result.put("progressPercent", 10);
        result.put("message", "Workbook submitted and queued for grading");
        result.put("quotaSummary", consumeResult.getQuotaSummary());
        result.put("reused", false);
        return result;
    }

    public Essay getDetail(Long id, Long teacherId) {
        Essay essay = requireOwnedEssay(id, teacherId);
        ensureAnnotatedImages(essay);
        return essayMapper.selectById(id);
    }

    public List<Essay> listByAssignment(Long assignmentId, Long teacherId) {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !teacherId.equals(assignment.getTeacherId())) {
            throw new IllegalArgumentException("作业不存在或无权操作");
        }

        LambdaQueryWrapper<Essay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Essay::getAssignmentId, assignmentId)
                .orderByDesc(Essay::getCreatedAt);
        List<Essay> essays = essayMapper.selectList(wrapper);

        Map<Long, Essay> latest = new LinkedHashMap<>();
        for (Essay e : essays) {
            latest.putIfAbsent(e.getStudentId(), e);
        }
        List<Essay> deduped = new ArrayList<>(latest.values());
        for (Essay e : deduped) {
            Student s = studentMapper.selectById(e.getStudentId());
            if (s != null) {
                e.setStudentName(s.getName());
            }
        }
        return deduped;
    }

    public Map<String, Object> regrade(Long essayId, Long teacherId) {
        Essay essay = requireOwnedEssay(essayId, teacherId);
        Assignment assignment = assignmentMapper.selectById(essay.getAssignmentId());
        if (assignment == null) {
            throw new IllegalArgumentException("作业不存在");
        }

        boolean workbook = TYPE_WORKBOOK.equalsIgnoreCase(assignment.getAssignmentType());
        List<File> imageFiles = resolveImageFiles(essay);
        if (imageFiles.isEmpty()) {
            throw new IllegalArgumentException("提交图片不存在，无法重新评分");
        }

        resetForRegrade(essayId);
        if (workbook) {
            gradingExecutor.submit(() -> processWorkbookAsync(
                    essayId, assignment.getId(), essay.getStudentId(), teacherId, null));
        } else {
            gradingExecutor.submit(() -> processEssayAsync(
                    essayId, assignment.getId(), essay.getStudentId(), teacherId, null));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("essayId", essayId);
        result.put("status", STATUS_OCR_PROCESSING);
        result.put("progressPercent", 30);
        result.put("message", "已开始重新评分");
        result.put("quotaSummary", teacherQuotaService.getQuotaSummary(teacherId));
        return result;
    }

    public QuotaSummaryDTO getTeacherQuota(Long teacherId) {
        return teacherQuotaService.getQuotaSummary(teacherId);
    }

    public Essay updateFinalScore(Long essayId, BigDecimal finalScore, Long teacherId) {
        EssayReviewDTO review = new EssayReviewDTO();
        review.setFinalScore(finalScore);
        return reviewEssay(essayId, review, teacherId);
    }

    public Essay reviewEssay(Long essayId, EssayReviewDTO review, Long teacherId) {
        Essay essay = requireOwnedEssay(essayId, teacherId);
        Assignment assignment = assignmentMapper.selectById(essay.getAssignmentId());
        if (assignment == null) {
            throw new IllegalArgumentException("作业不存在");
        }
        BigDecimal finalScore = review.getFinalScore();
        if (finalScore.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("最终得分不能小于0");
        }
        BigDecimal maxScore = BigDecimal.valueOf(assignment.getTotalScore());
        if (finalScore.compareTo(maxScore) > 0) {
            throw new IllegalArgumentException("最终得分不能超过作业满分");
        }
        essay.setFinalScore(finalScore);
        essay.setStatus(STATUS_TEACHER_REVIEWED);
        essay.setReviewRequired(false);
        essay.setTeacherReviewNote(review.getTeacherReviewNote() == null ? "" : review.getTeacherReviewNote().trim());
        String feedbackJson = null;
        if (review.getFeedback() != null && !review.getFeedback().isEmpty()) {
            feedbackJson = JSONUtil.toJsonStr(review.getFeedback());
            essay.setAiFeedback(feedbackJson);
            if (TYPE_WORKBOOK.equalsIgnoreCase(assignment.getAssignmentType())) {
                essay.setWorkbookResultJson(feedbackJson);
            }
        }
        if (feedbackJson == null) {
            feedbackJson = TYPE_WORKBOOK.equalsIgnoreCase(assignment.getAssignmentType())
                    ? essay.getWorkbookResultJson()
                    : essay.getAiFeedback();
        }
        applyAnnotatedImages(essay, essay, feedbackJson);
        essay.setGradingCompletedAt(LocalDateTime.now());
        essayMapper.updateById(essay);
        return essayMapper.selectById(essayId);
    }

    public Map<String, Object> getProgress(Long essayId, Long teacherId) {
        Essay essay = requireOwnedEssay(essayId, teacherId);
        Map<String, Object> progress = new HashMap<>();
        progress.put("essayId", essay.getId());
        progress.put("status", essay.getStatus());
        progress.put("statusText", statusText(essay.getStatus()));
        progress.put("progressPercent", progressPercent(essay.getStatus()));
        progress.put("failureReason", essay.getFailureReason());
        progress.put("failureMessage", essay.getFailureMessage());
        progress.put("confidenceScore", essay.getConfidenceScore());
        progress.put("confidenceLevel", essay.getConfidenceLevel());
        progress.put("reviewRequired", essay.getReviewRequired());
        progress.put("teacherReviewNote", essay.getTeacherReviewNote());
        progress.put("retriable", STATUS_FAILED.equals(essay.getStatus()));
        if (STATUS_GRADED.equals(essay.getStatus()) || STATUS_TEACHER_REVIEWED.equals(essay.getStatus())) {
            progress.put("ocrText", essay.getOcrText());
            progress.put("gradingResult", parseStoredGradingResult(essay));
            progress.put("workbookResult", parseWorkbookResult(essay.getWorkbookResultJson()));
            progress.put("finalScore", essay.getFinalScore());
        }
        return progress;
    }

    private void processEssayAsync(Long essayId, Long assignmentId, Long studentId, Long teacherId,
                                   TeacherQuotaService.ConsumeResult consumeResult) {
        try {
            Assignment assignment = assignmentMapper.selectById(assignmentId);
            if (assignment == null) {
                failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                        "ASSIGNMENT_MISSING", "作业不存在，批改已停止");
                return;
            }
            Essay essay = essayMapper.selectById(essayId);
            if (essay == null) {
                return;
            }
            File imageFile = resolveImagePath(essay.getImageUrl()).toFile();

            GradingResultDTO gradingResult;
            // MinerU 解析 -> AI 文本评分（数学/语文/英语统一走此路径）
            updateStatus(essayId, STATUS_OCR_PROCESSING, null, null);
            MineruService.ParseResult parseResult = mineruService.parse(imageFile);
            String ocrText = parseResult == null ? "" : parseResult.toAnalysisText();
            if (ocrText == null || ocrText.trim().isEmpty()) {
                failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                        "MINERU_FAILED", "作文解析失败，请确认图片清晰后重试");
                return;
            }
            Essay ocrDone = new Essay();
            ocrDone.setId(essayId);
            ocrDone.setOcrText(ocrText);
            ocrDone.setMineruMarkdown(parseResult.getMarkdown());
            ocrDone.setMineruStructureJson(parseResult.getStructureJson());
            ocrDone.setStatus(STATUS_OCR_DONE);
            essayMapper.updateById(ocrDone);
            updateStatus(essayId, STATUS_AI_PROCESSING, null, null);
            gradingResult = aiGradingService.grade(assignment, ocrText);

            if (gradingResult == null) {
                failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                        "AI_GRADING_FAILED", "AI评分失败，请稍后重试");
                return;
            }

            Essay graded = new Essay();
            graded.setId(essayId);
            graded.setAiScore(gradingResult.getTotalScore());
            BigDecimal confidenceScore = normalizeConfidence(gradingResult.getConfidenceScore());
            boolean reviewRequired = Boolean.TRUE.equals(gradingResult.getReviewRequired())
                    || needsTeacherReview(confidenceScore);
            gradingResult.setConfidenceScore(confidenceScore);
            gradingResult.setConfidenceLevel(confidenceLevel(confidenceScore));
            gradingResult.setReviewRequired(reviewRequired);
            String feedbackJson = JSONUtil.toJsonStr(gradingResult);
            graded.setAiFeedback(feedbackJson);
            graded.setAiRawResponse(aiGradingService.getLastRawResponse());
            graded.setConfidenceScore(confidenceScore);
            graded.setConfidenceLevel(confidenceLevel(confidenceScore));
            graded.setReviewRequired(reviewRequired);
            graded.setTeacherReviewNote("");
            graded.setStatus(STATUS_GRADED);
            graded.setFailureReason("");
            graded.setFailureMessage("");
            graded.setGradingCompletedAt(LocalDateTime.now());
            applyAnnotatedImages(graded, essay, feedbackJson);
            essayMapper.updateById(graded);
        } catch (Exception e) {
            log.error("Async grading failed, essayId={}", essayId, e);
            failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                    "SYSTEM_ERROR", "批改任务异常：" + e.getMessage());
        }
    }

    private void processWorkbookAsync(Long essayId, Long assignmentId, Long studentId, Long teacherId,
                                      TeacherQuotaService.ConsumeResult consumeResult) {
        try {
            Assignment assignment = assignmentMapper.selectById(assignmentId);
            if (assignment == null) {
                failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                        "ASSIGNMENT_MISSING", "Assignment missing, grading stopped");
                return;
            }
            Essay essay = essayMapper.selectById(essayId);
            if (essay == null) {
                return;
            }
            List<File> imageFiles = resolveImageFiles(essay);
            if (imageFiles.isEmpty()) {
                failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                        "IMAGE_MISSING", "Workbook images are missing");
                return;
            }
            updateStatus(essayId, STATUS_OCR_PROCESSING, null, null);
            List<MineruService.ParseResult> parseResults = new ArrayList<>();
            for (File imageFile : imageFiles) {
                MineruService.ParseResult pageResult = mineruService.parse(imageFile);
                if (pageResult == null || !pageResult.hasContent()) {
                    failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                            "MINERU_FAILED", "Workbook image parsing failed. Please upload clearer images.");
                    return;
                }
                parseResults.add(pageResult);
            }
            MineruService.ParseResult mergedParseResult = mineruService.mergeResults(parseResults);
            String studentAnalysisText = mergedParseResult.toAnalysisText();
            if (isBlank(studentAnalysisText)) {
                failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                        "MINERU_EMPTY", "Workbook parsing returned empty text.");
                return;
            }
            Essay ocrDone = new Essay();
            ocrDone.setId(essayId);
            ocrDone.setOcrText(studentAnalysisText);
            ocrDone.setMineruMarkdown(mergedParseResult.getMarkdown());
            ocrDone.setMineruStructureJson(mergedParseResult.getStructureJson());
            ocrDone.setStatus(STATUS_OCR_DONE);
            essayMapper.updateById(ocrDone);

            updateStatus(essayId, STATUS_AI_PROCESSING, null, null);
            String workbookResultJson = aiGradingService.gradeWorkbookFromStructure(
                    assignment, assignment.getAnswerKeyJson(), studentAnalysisText);
            if (isBlank(workbookResultJson)) {
                failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                        "AI_GRADING_FAILED", "Workbook AI grading failed");
                return;
            }
            BigDecimal score = extractWorkbookScore(workbookResultJson);
            BigDecimal confidenceScore = extractWorkbookConfidence(workbookResultJson);
            boolean reviewRequired = extractWorkbookReviewRequired(workbookResultJson)
                    || needsTeacherReview(confidenceScore);
            Essay graded = new Essay();
            graded.setId(essayId);
            graded.setAiScore(score);
            graded.setConfidenceScore(confidenceScore);
            graded.setConfidenceLevel(confidenceLevel(confidenceScore));
            graded.setReviewRequired(reviewRequired);
            graded.setTeacherReviewNote("");
            graded.setAiFeedback(workbookResultJson);
            graded.setWorkbookResultJson(workbookResultJson);
            graded.setAiRawResponse(aiGradingService.getLastRawResponse());
            graded.setStatus(STATUS_GRADED);
            graded.setFailureReason("");
            graded.setFailureMessage("");
            graded.setGradingCompletedAt(LocalDateTime.now());
            applyAnnotatedImages(graded, essay, workbookResultJson);
            essayMapper.updateById(graded);
        } catch (Exception e) {
            log.error("Async workbook grading failed, essayId={}", essayId, e);
            failEssay(essayId, teacherId, consumeResult, assignmentId, studentId,
                    "SYSTEM_ERROR", "Workbook grading task error: " + e.getMessage());
        }
    }

    private void failEssay(Long essayId, Long teacherId, TeacherQuotaService.ConsumeResult consumeResult,
                           Long assignmentId, Long studentId, String reason, String message) {
        if (consumeResult != null) {
            teacherQuotaService.refundQuota(teacherId, consumeResult, assignmentId, studentId,
                    message + "，返还额度");
        }
        updateStatus(essayId, STATUS_FAILED, reason, message);
    }

    private void resetForRegrade(Long essayId) {
        essayMapper.update(null, new LambdaUpdateWrapper<Essay>()
                .eq(Essay::getId, essayId)
                .set(Essay::getAiScore, null)
                .set(Essay::getFinalScore, null)
                .set(Essay::getConfidenceScore, null)
                .set(Essay::getConfidenceLevel, "")
                .set(Essay::getReviewRequired, false)
                .set(Essay::getTeacherReviewNote, "")
                .set(Essay::getOcrText, "")
                .set(Essay::getMineruMarkdown, "")
                .set(Essay::getMineruStructureJson, "")
                .set(Essay::getAiFeedback, "")
                .set(Essay::getWorkbookResultJson, "")
                .set(Essay::getAiRawResponse, "")
                .set(Essay::getAnnotatedImageUrl, "")
                .set(Essay::getAnnotatedImageUrls, "")
                .set(Essay::getFailureReason, "")
                .set(Essay::getFailureMessage, "")
                .set(Essay::getStatus, STATUS_OCR_PROCESSING)
                .set(Essay::getGradingStartedAt, LocalDateTime.now()));
    }

    private void updateStatus(Long essayId, String status, String failureReason, String failureMessage) {
        Essay essay = new Essay();
        essay.setId(essayId);
        essay.setStatus(status);
        essay.setFailureReason(failureReason == null ? "" : failureReason);
        essay.setFailureMessage(failureMessage == null ? "" : failureMessage);
        if (STATUS_FAILED.equals(status)) {
            essay.setGradingCompletedAt(LocalDateTime.now());
        }
        essayMapper.updateById(essay);
    }

    private Essay findExistingEssay(Long assignmentId, Long studentId, String contentHash) {
        LambdaQueryWrapper<Essay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Essay::getAssignmentId, assignmentId)
                .eq(Essay::getStudentId, studentId)
                .eq(Essay::getContentHash, contentHash)
                .in(Essay::getStatus, STATUS_GRADED, STATUS_TEACHER_REVIEWED)
                .orderByDesc(Essay::getCreatedAt)
                .last("LIMIT 1");
        return essayMapper.selectOne(wrapper);
    }

    private Essay requireOwnedEssay(Long essayId, Long teacherId) {
        Essay essay = essayMapper.selectById(essayId);
        if (essay == null) {
            throw new IllegalArgumentException("批改记录不存在");
        }
        Assignment assignment = assignmentMapper.selectById(essay.getAssignmentId());
        if (assignment == null || !teacherId.equals(assignment.getTeacherId())) {
            throw new IllegalArgumentException("无权操作该批改记录");
        }
        return essay;
    }

    private Path resolveImagePath(String imageUrl) {
        Path imagePath = Paths.get(uploadPath);
        if (!imagePath.isAbsolute()) {
            imagePath = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
        }
        return imagePath.resolve(imageUrl);
    }

    private void applyAnnotatedImages(Essay updateTarget, Essay sourceEssay, String feedbackJson) {
        if (updateTarget == null || sourceEssay == null || isBlank(feedbackJson)) {
            return;
        }
        try {
            AnnotationImageService.RenderResult renderResult =
                    annotationImageService.renderForEssay(sourceEssay, feedbackJson);
            if (renderResult.hasImages()) {
                updateTarget.setAnnotatedImageUrl(renderResult.firstImageUrl());
                updateTarget.setAnnotatedImageUrls(JSONUtil.toJsonStr(renderResult.getImageUrls()));
            }
        } catch (Exception e) {
            log.warn("Failed to generate annotated image for essay {}", sourceEssay.getId(), e);
        }
    }

    private void ensureAnnotatedImages(Essay essay) {
        if (essay == null
                || (!STATUS_GRADED.equals(essay.getStatus()) && !STATUS_TEACHER_REVIEWED.equals(essay.getStatus()))
                || !isBlank(essay.getAnnotatedImageUrl())) {
            return;
        }
        String feedbackJson = !isBlank(essay.getWorkbookResultJson())
                ? essay.getWorkbookResultJson()
                : essay.getAiFeedback();
        Essay update = new Essay();
        update.setId(essay.getId());
        applyAnnotatedImages(update, essay, feedbackJson);
        if (!isBlank(update.getAnnotatedImageUrl())) {
            essayMapper.updateById(update);
        }
    }

    private GradingResultDTO parseStoredGradingResult(Essay essay) {
        if (!isBlank(essay.getWorkbookResultJson())) {
            GradingResultDTO fallback = new GradingResultDTO();
            fallback.setTotalScore(essay.getAiScore() != null ? essay.getAiScore() : BigDecimal.ZERO);
            try {
                cn.hutool.json.JSONObject json = JSONUtil.parseObj(essay.getWorkbookResultJson());
                fallback.setOverallComment(json.getStr("overallComment"));
            } catch (Exception ignored) {
                fallback.setOverallComment("Workbook grading completed");
            }
            return fallback;
        }
        if (essay.getAiFeedback() != null && !essay.getAiFeedback().trim().isEmpty()) {
            try {
                return JSONUtil.toBean(essay.getAiFeedback(), GradingResultDTO.class);
            } catch (Exception e) {
                log.warn("Failed to parse stored grading result for essay {}", essay.getId(), e);
            }
        }

        GradingResultDTO fallback = new GradingResultDTO();
        fallback.setTotalScore(essay.getAiScore() != null ? essay.getAiScore() : BigDecimal.ZERO);
        fallback.setOverallComment(essay.getAiFeedback());
        return fallback;
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
            log.error("Failed to save image", e);
            return null;
        }
    }

    private List<String> saveImages(List<byte[]> imageBytesList) {
        List<String> imageUrls = new ArrayList<>();
        for (byte[] imageBytes : imageBytesList) {
            String imageUrl = saveImage(imageBytes);
            if (imageUrl != null) {
                imageUrls.add(imageUrl);
            }
        }
        return imageUrls;
    }

    private String hashImages(List<byte[]> imageBytesList) {
        StringBuilder builder = new StringBuilder();
        for (byte[] imageBytes : imageBytesList) {
            builder.append(DigestUtil.sha256Hex(imageBytes)).append('|');
        }
        return DigestUtil.sha256Hex(builder.toString());
    }

    private List<File> resolveImageFiles(Essay essay) {
        List<File> files = new ArrayList<>();
        List<String> urls = parseImageUrls(essay);
        for (String imageUrl : urls) {
            File file = resolveImagePath(imageUrl).toFile();
            if (file.exists()) {
                files.add(file);
            }
        }
        return files;
    }

    private List<String> parseImageUrls(Essay essay) {
        List<String> urls = new ArrayList<>();
        if (!isBlank(essay.getImageUrls())) {
            try {
                cn.hutool.json.JSONArray arr = JSONUtil.parseArray(essay.getImageUrls());
                for (Object item : arr) {
                    if (item != null && !isBlank(String.valueOf(item))) {
                        urls.add(String.valueOf(item));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (urls.isEmpty() && !isBlank(essay.getImageUrl())) {
            urls.add(essay.getImageUrl());
        }
        return urls;
    }

    private Object parseWorkbookResult(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return json;
        }
    }

    private BigDecimal extractWorkbookScore(String json) {
        try {
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(json);
            Object value = obj.get("totalScore");
            if (value == null) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal extractWorkbookConfidence(String json) {
        try {
            Object value = JSONUtil.parseObj(json).get("confidenceScore");
            return normalizeConfidence(value == null ? null : new BigDecimal(String.valueOf(value)));
        } catch (Exception e) {
            return DEFAULT_CONFIDENCE;
        }
    }

    private boolean extractWorkbookReviewRequired(String json) {
        try {
            return JSONUtil.parseObj(json).getBool("reviewRequired", false);
        } catch (Exception e) {
            return false;
        }
    }

    private BigDecimal normalizeConfidence(BigDecimal confidenceScore) {
        if (confidenceScore == null) return DEFAULT_CONFIDENCE;
        if (confidenceScore.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (confidenceScore.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return confidenceScore;
    }

    private boolean needsTeacherReview(BigDecimal confidenceScore) {
        return normalizeConfidence(confidenceScore).compareTo(REVIEW_CONFIDENCE_THRESHOLD) < 0;
    }

    private String confidenceLevel(BigDecimal confidenceScore) {
        BigDecimal score = normalizeConfidence(confidenceScore);
        if (score.compareTo(new BigDecimal("0.9000")) >= 0) return "HIGH";
        if (score.compareTo(REVIEW_CONFIDENCE_THRESHOLD) >= 0) return "MEDIUM";
        return "LOW";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int progressPercent(String status) {
        if (STATUS_UPLOADED.equals(status)) return 10;
        if (STATUS_OCR_PROCESSING.equals(status)) return 30;
        if (STATUS_OCR_DONE.equals(status)) return 55;
        if (STATUS_AI_PROCESSING.equals(status)) return 75;
        if (STATUS_GRADED.equals(status) || STATUS_TEACHER_REVIEWED.equals(status)) return 100;
        if (STATUS_FAILED.equals(status)) return 100;
        return 0;
    }

    private String statusText(String status) {
        if (STATUS_UPLOADED.equals(status)) return "作文已上传";
        if (STATUS_OCR_PROCESSING.equals(status)) return "正在识别作文文字";
        if (STATUS_OCR_DONE.equals(status)) return "文字识别完成";
        if (STATUS_AI_PROCESSING.equals(status)) return "正在进行AI评分";
        if (STATUS_GRADED.equals(status)) return "AI评分完成，等待老师确认";
        if (STATUS_TEACHER_REVIEWED.equals(status)) return "老师已确认最终成绩";
        if (STATUS_FAILED.equals(status)) return "批改失败";
        return "等待处理";
    }

    @PreDestroy
    public void shutdownExecutor() {
        gradingExecutor.shutdown();
    }
}
