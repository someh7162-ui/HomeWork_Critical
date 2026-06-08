package com.papercritical.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.papercritical.dto.GradingResultDTO;
import com.papercritical.entity.Assignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AiGradingService {

    private static final Logger log = LoggerFactory.getLogger(AiGradingService.class);
    private final ThreadLocal<String> lastRawResponse = new ThreadLocal<>();

    @Value("${ai.provider:mimo}")
    private String provider;

    @Value("${ai.mimo.api-key}")
    private String mimoApiKey;
    @Value("${ai.mimo.api-url}")
    private String mimoApiUrl;
    @Value("${ai.mimo.model}")
    private String mimoModel;
    @Value("${ai.mimo.vision-model:mimo-v2-omni}")
    private String mimoVisionModel;

    @Value("${ai.qwen.api-key}")
    private String qwenApiKey;
    @Value("${ai.qwen.api-url}")
    private String qwenApiUrl;
    @Value("${ai.qwen.model}")
    private String qwenModel;

    private String apiKey() { return "qwen".equalsIgnoreCase(provider) ? qwenApiKey : mimoApiKey; }
    private String apiUrl() { return "qwen".equalsIgnoreCase(provider) ? qwenApiUrl : mimoApiUrl; }
    private String model() { return "qwen".equalsIgnoreCase(provider) ? qwenModel : mimoModel; }
    private String visionModel() { return mimoVisionModel; }

    @PostConstruct
    public void init() {
        log.info("AI Grading provider={}, textModel={}, visionModel={}", provider, model(), visionModel());
    }

    private static final String SYSTEM_PROMPT_EN =
            "你是一位资深的高考英语/四六级写作阅卷老师。请根据题目要求、参考范文和评分标准，对学生的作文进行严格但公正的评分。" +
            "你需要从内容要点、语法与词汇、结构连贯性、卷面书写等多个维度进行评分。" +
            "请只返回JSON格式的结果，不要包含任何其他文字。";

    private static final String VISION_SYSTEM_PROMPT_EN =
            "你是一位资深的高考英语/四六级写作阅卷老师。你会收到学生作文的图片。" +
            "请直接根据图片中的作文内容进行评分，同时结合题目要求、参考范文和评分标准。" +
            "你需要从内容要点、语法与词汇、结构连贯性、卷面书写等多个维度进行评分。" +
            "请只返回JSON格式的结果，不要包含任何其他文字。";

    private static final String SYSTEM_PROMPT_CN =
            "你是一位资深的高考语文/中考语文阅卷老师。请根据题目要求、参考范文和评分标准，对学生的作文进行严格但公正的评分。" +
            "你需要从立意与中心、语言与修辞、结构与逻辑、卷面书写等多个维度进行评分。" +
            "请只返回JSON格式的结果，不要包含任何其他文字。";

    private static final String VISION_SYSTEM_PROMPT_CN =
            "你是一位资深的高考语文/中考语文阅卷老师。你会收到学生作文的图片。" +
            "请直接根据图片中的作文内容进行评分，同时结合题目要求、参考范文和评分标准。" +
            "你需要从立意与中心、语言与修辞、结构与逻辑、卷面书写等多个维度进行评分。" +
            "请只返回JSON格式的结果，不要包含任何其他文字。";

    private static final String SYSTEM_PROMPT_MATH =
            "你是一位资深的高中数学/中考数学阅卷老师。请根据题目要求、参考答案和评分标准，对学生的数学解答进行严格但公正的评分。" +
            "你需要从解题思路、计算过程、答案正确性、卷面书写等多个维度进行评分。" +
            "请只返回JSON格式的结果，不要包含任何其他文字。";

    private static final String VISION_SYSTEM_PROMPT_MATH =
            "你是一位资深的高中数学/中考数学阅卷老师。你会收到学生数学解答的图片。" +
            "请直接根据图片中的解答内容进行评分，同时结合题目要求、参考答案和评分标准。" +
            "你需要从解题思路、计算过程、答案正确性、卷面书写等多个维度进行评分。" +
            "请只返回JSON格式的结果，不要包含任何其他文字。";

    private static final String CHINESE_OUTPUT_DIRECTIVE =
            "\n语言要求（最高优先级）：返回 JSON 中所有面向师生的自然语言文字——评语、点评、解析、反馈、知识点名称、巩固建议、批注说明——必须使用简体中文，" +
            "对应字段包括 overallComment、feedback、comment、analysis、knowledgePoint、name、suggestion 以及 annotations 内的 comment。" +
            "但 studentAnswer、correctAnswer、answer 等答案内容字段保持题目本身的语言不变（例如英语题的答案可为英文）。" +
            "JSON 的字段名、数字、布尔值和固定枚举值（HIGH/MEDIUM/LOW、severity 的 error/warn 等）保持英文不变。不要在上述中文字段里输出英文句子。";

    private String getSystemPrompt(String subject) {
        if ("语文".equals(subject)) return SYSTEM_PROMPT_CN;
        if ("数学".equals(subject)) return SYSTEM_PROMPT_MATH;
        return SYSTEM_PROMPT_EN;
    }

    private String getVisionSystemPrompt(String subject) {
        if ("语文".equals(subject)) return VISION_SYSTEM_PROMPT_CN;
        if ("数学".equals(subject)) return VISION_SYSTEM_PROMPT_MATH;
        return VISION_SYSTEM_PROMPT_EN;
    }

    public GradingResultDTO gradeByVision(Assignment assignment, File imageFile) {
        try {
            lastRawResponse.remove();
            String subject = assignment.getSubject() != null ? assignment.getSubject() : "英语";
            String base64Image = encodeImageToBase64(imageFile);
            String userPrompt = buildVisionPrompt(assignment, subject);

            JSONObject requestBody = new JSONObject();
            requestBody.set("model", visionModel());
            requestBody.set("max_tokens", 8192);
            requestBody.set("temperature", 0);

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.set("role", "system");
            sysMsg.set("content", getVisionSystemPrompt(subject));
            messages.add(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.set("role", "user");
            JSONArray content = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.set("type", "text");
            textPart.set("text", userPrompt);
            content.add(textPart);

            JSONObject imagePart = new JSONObject();
            imagePart.set("type", "image_url");
            JSONObject imageUrlObj = new JSONObject();
            imageUrlObj.set("url", "data:image/jpeg;base64," + base64Image);
            imagePart.set("image_url", imageUrlObj);
            content.add(imagePart);

            userMsg.set("content", content);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            log.info("Vision grading request: model={}, imageSize={} chars", visionModel(), base64Image.length());

            HttpResponse<String> response = sendRequest(requestBody);
            if (response.statusCode() != 200) {
                log.error("Vision model API error: {} - {}", response.statusCode(), response.body());
                return null;
            }

            String respBody = response.body();
            log.info("Vision API response body: {}", respBody != null ? respBody : "null");

            JSONObject respJson = JSONUtil.parseObj(respBody);
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("Vision API returned no choices: {}", respBody);
                return null;
            }

            String contentStr = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            if (contentStr == null || contentStr.trim().isEmpty()) {
                log.error("Vision API returned empty content. Full response: {}", respBody);
                return null;
            }
            lastRawResponse.set(contentStr);
            return parseResult(contentStr);
        } catch (Exception e) {
            log.error("Vision grading failed", e);
            return null;
        }
    }

    public GradingResultDTO grade(Assignment assignment, String ocrText) {
        String subject = assignment.getSubject() != null ? assignment.getSubject() : "英语";
        String userPrompt = buildPrompt(assignment, ocrText, subject);
        try {
            lastRawResponse.remove();
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", model());
            requestBody.set("max_tokens", 8192);
            requestBody.set("temperature", 0);

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.set("role", "system");
            sysMsg.set("content", getSystemPrompt(subject));
            messages.add(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.set("role", "user");
            userMsg.set("content", userPrompt);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            HttpResponse<String> response = sendRequest(requestBody);
            if (response.statusCode() != 200) {
                log.error("MiMo API error: {} - {}", response.statusCode(), response.body());
                return null;
            }

            String respBody = response.body();
            log.info("Text grading API response body: {}", respBody != null ? respBody : "null");

            JSONObject respJson = JSONUtil.parseObj(respBody);
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("Text grading API returned no choices: {}", respBody);
                return null;
            }

            String content = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            if (content == null || content.trim().isEmpty()) {
                log.error("Text grading API returned empty content. Full response: {}", respBody);
                return null;
            }
            lastRawResponse.set(content);
            return parseResult(content);
        } catch (Exception e) {
            log.error("AI grading failed", e);
            return null;
        }
    }

    public String getLastRawResponse() {
        return lastRawResponse.get();
    }

    public String parseWorkbookAnswerKey(Assignment assignment, List<File> imageFiles) {
        try {
            lastRawResponse.remove();
            String prompt = String.format(
                    "You are an education answer-key extraction assistant. Extract a structured answer key from the uploaded workbook answer images.\n" +
                    "Subject: %s\nQuestion range: %s\nTotal score: %d\n" +
                    "Return JSON only, no markdown. The JSON schema is:\n" +
                    "{\"questionRange\":\"...\",\"totalScore\":%d,\"questions\":[{\"questionNo\":\"1\",\"answer\":\"standard answer\",\"analysis\":\"short explanation\",\"knowledgePoint\":\"knowledge point\",\"maxScore\":1}]}\n" +
                    "Only include questions inside the requested question range. If score per question is not visible, distribute the total score reasonably.",
                    safe(assignment.getSubject()), safe(assignment.getQuestionRange()), assignment.getTotalScore(),
                    assignment.getTotalScore());
            String content = sendVisionJsonPrompt(
                    "You extract workbook answer keys into strict JSON for grading.",
                    prompt,
                    imageFiles,
                    12000);
            return normalizeJsonObject(content);
        } catch (Exception e) {
            log.error("Workbook answer key parsing failed", e);
            return null;
        }
    }

    public String gradeWorkbook(Assignment assignment, String answerKeyJson, List<File> imageFiles) {
        try {
            lastRawResponse.remove();
            String prompt = String.format(
                    "You are a strict but fair multi-subject workbook grader. Grade the student's uploaded workbook pages against the answer key.\n" +
                    "Subject: %s\nQuestion range: %s\nTotal score: %d\n" +
                    "Answer key JSON:\n%s\n\n" +
                    "Return JSON only, no markdown. Required schema:\n" +
                    "{\"totalScore\":0,\"overallComment\":\"short summary\",\"confidenceScore\":0.88,\"confidenceLevel\":\"HIGH|MEDIUM|LOW\",\"reviewRequired\":false,\"questions\":[{\"questionNo\":\"1\",\"studentAnswer\":\"student answer or blank\",\"correctAnswer\":\"standard answer\",\"isCorrect\":true,\"score\":1,\"maxScore\":1,\"knowledgePoint\":\"knowledge point\",\"feedback\":\"short feedback\",\"annotations\":[{\"pageIndex\":0,\"type\":\"rect\",\"x\":0.1,\"y\":0.2,\"width\":0.2,\"height\":0.08,\"comment\":\"short visible note\",\"severity\":\"error\"}]}],\"knowledgePoints\":[{\"name\":\"knowledge point\",\"wrongCount\":0,\"totalCount\":1,\"suggestion\":\"practice suggestion\"}],\"annotations\":[{\"pageIndex\":0,\"type\":\"rect\",\"x\":0.1,\"y\":0.2,\"width\":0.2,\"height\":0.08,\"comment\":\"short visible note\",\"severity\":\"error\",\"questionNo\":\"1\"}]}\n" +
                    "Annotation rules: add annotations for clear wrong or incomplete answer areas that are visible in the uploaded images. Coordinates must be relative numbers from 0 to 1 on the displayed page image. pageIndex is zero-based in upload order. Use empty annotations [] when the position is uncertain.\n" +
                    "Confidence rules: confidenceScore is a number from 0 to 1 based on image legibility, answer-key clarity, question matching, and grading certainty. Use reviewRequired=true when confidenceScore is below 0.75 or any answer requires teacher judgment.\n" +
                    "Rules: grade only questions inside the question range; give partial credit when appropriate; do not punish unclear OCR/vision artifacts unless the student answer is truly wrong; totalScore must equal the sum of question scores and must not exceed the assignment total score.",
                    safe(assignment.getSubject()), safe(assignment.getQuestionRange()), assignment.getTotalScore(),
                    answerKeyJson);
            String content = sendVisionJsonPrompt(
                    "You grade workbook submissions into strict JSON with per-question scores and knowledge points.",
                    prompt,
                    imageFiles,
                    16000);
            JSONObject json = JSONUtil.parseObj(normalizeJsonObject(content));
            if (!json.containsKey("totalScore")) {
                json.set("totalScore", 0);
            }
            if (!json.containsKey("overallComment")) {
                json.set("overallComment", "");
            }
            if (!json.containsKey("questions")) {
                json.set("questions", new JSONArray());
            }
            if (!json.containsKey("knowledgePoints")) {
                json.set("knowledgePoints", new JSONArray());
            }
            if (!json.containsKey("annotations")) {
                json.set("annotations", collectQuestionAnnotations(json.getJSONArray("questions")));
            }
            double confidenceScore = clampConfidence(json.getDouble("confidenceScore", 0.78D));
            json.set("confidenceScore", confidenceScore);
            json.set("confidenceLevel", confidenceLevel(confidenceScore));
            json.set("reviewRequired", json.getBool("reviewRequired", false) || confidenceScore < 0.75D);
            return json.toString();
        } catch (Exception e) {
            log.error("Workbook grading failed", e);
            return null;
        }
    }

    public String parseWorkbookAnswerKeyFromStructure(Assignment assignment, String mineruAnalysisText) {
        try {
            lastRawResponse.remove();
            String prompt = String.format(
                    "You are the answer-key parsing Agent in PaperCritical. The teacher uploaded workbook answer-key photos. " +
                    "Those photos have already been parsed by MinerU full parsing into Markdown plus structured layout text.\n" +
                    "Your job is to extract a clean structured answer key from that MinerU result, not to call OCR again.\n\n" +
                    "Subject: %s\nQuestion range: %s\nTotal score: %d\n\n" +
                    "MinerU parsed answer-key content:\n%s\n\n" +
                    "Return JSON only, no markdown. Required schema:\n" +
                    "{\"questionRange\":\"...\",\"totalScore\":%d,\"questions\":[{\"questionNo\":\"1\",\"answer\":\"standard answer\",\"analysis\":\"short explanation\",\"knowledgePoint\":\"knowledge point\",\"maxScore\":1}]}\n" +
                    "Rules: only include questions inside the requested question range; ignore parser noise, URLs, figure captions, headers and footers; " +
                    "merge multi-page content in page order; if score per question is not visible, distribute the total score reasonably." + CHINESE_OUTPUT_DIRECTIVE,
                    safe(assignment.getSubject()), safe(assignment.getQuestionRange()), totalScore(assignment),
                    safe(mineruAnalysisText), totalScore(assignment));
            String content = sendTextJsonPrompt(
                    "You extract workbook answer keys from MinerU structured parsing output into strict JSON.",
                    prompt,
                    12000);
            return normalizeJsonObject(content);
        } catch (Exception e) {
            log.error("Workbook answer key parsing from MinerU structure failed", e);
            return null;
        }
    }

    public String gradeWorkbookFromStructure(Assignment assignment, String answerKeyJson, String studentMineruAnalysisText) {
        try {
            lastRawResponse.remove();
            String prompt = String.format(
                    "You are the workbook grading Agent in PaperCritical. The student's workbook photos have already been parsed by MinerU full parsing " +
                    "into Markdown plus structured layout text. Grade from that structured content against the teacher's answer key.\n\n" +
                    "Subject: %s\nQuestion range: %s\nTotal score: %d\n\n" +
                    "Answer key JSON:\n%s\n\n" +
                    "MinerU parsed student workbook content:\n%s\n\n" +
                    "Return JSON only, no markdown. Required schema:\n" +
                    "{\"totalScore\":0,\"overallComment\":\"short summary\",\"confidenceScore\":0.88,\"confidenceLevel\":\"HIGH|MEDIUM|LOW\",\"reviewRequired\":false,\"questions\":[{\"questionNo\":\"1\",\"studentAnswer\":\"student answer or blank\",\"correctAnswer\":\"standard answer\",\"isCorrect\":true,\"score\":1,\"maxScore\":1,\"knowledgePoint\":\"knowledge point\",\"feedback\":\"short feedback\",\"annotations\":[{\"pageIndex\":0,\"type\":\"rect\",\"x\":0.1,\"y\":0.2,\"width\":0.2,\"height\":0.08,\"comment\":\"short visible note\",\"severity\":\"error\"}]}],\"knowledgePoints\":[{\"name\":\"knowledge point\",\"wrongCount\":0,\"totalCount\":1,\"suggestion\":\"practice suggestion\"}],\"annotations\":[{\"pageIndex\":0,\"type\":\"rect\",\"x\":0.1,\"y\":0.2,\"width\":0.2,\"height\":0.08,\"comment\":\"short visible note\",\"severity\":\"error\",\"questionNo\":\"1\"}]}\n" +
                    "Annotation rules: if MinerU structure contains page/bbox/layout information and the mistake location is confident, return rect annotations with relative coordinates from 0 to 1 on the original page. " +
                    "If exact coordinates are not available, return a pin annotation near the likely question area when pageIndex is confident; otherwise use empty annotations []. " +
                    "pageIndex is zero-based in upload order.\n" +
                    "Confidence rules: confidenceScore is 0 to 1 based on parsing legibility, question matching and grading certainty. Use reviewRequired=true below 0.75 or when teacher judgment is needed.\n" +
                    "Grading rules: grade only questions inside the question range; give partial credit when appropriate; ignore MinerU parser/OCR noise unless the student's real answer is wrong; totalScore must equal the sum of question scores and must not exceed the assignment total score." + CHINESE_OUTPUT_DIRECTIVE,
                    safe(assignment.getSubject()), safe(assignment.getQuestionRange()), totalScore(assignment),
                    safe(answerKeyJson), safe(studentMineruAnalysisText));
            String content = sendTextJsonPrompt(
                    "You grade workbook submissions from MinerU structured parsing output into strict JSON with per-question scores, knowledge points, and optional annotations.",
                    prompt,
                    16000);
            return normalizeWorkbookResultJson(content);
        } catch (Exception e) {
            log.error("Workbook grading from MinerU structure failed", e);
            return null;
        }
    }

    private HttpResponse<String> sendRequest(JSONObject requestBody) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl()))
                .header("Authorization", "Bearer " + apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(java.time.Duration.ofSeconds(180))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String sendTextJsonPrompt(String systemPrompt, String userPrompt, int maxTokens)
            throws IOException, InterruptedException {
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model());
        requestBody.set("max_tokens", maxTokens);
        requestBody.set("temperature", 0);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.set("role", "system");
        sysMsg.set("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");
        userMsg.set("content", userPrompt);
        messages.add(userMsg);
        requestBody.set("messages", messages);

        HttpResponse<String> response = sendRequest(requestBody);
        if (response.statusCode() != 200) {
            log.error("Text JSON API error: {} - {}", response.statusCode(), response.body());
            return null;
        }
        JSONObject respJson = JSONUtil.parseObj(response.body());
        JSONArray choices = respJson.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        String contentStr = choices.getJSONObject(0).getJSONObject("message").getStr("content");
        lastRawResponse.set(contentStr);
        return contentStr;
    }

    private String sendVisionJsonPrompt(String systemPrompt, String userPrompt, List<File> imageFiles,
                                        int maxTokens) throws IOException, InterruptedException {
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", visionModel());
        requestBody.set("max_tokens", maxTokens);
        requestBody.set("temperature", 0);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.set("role", "system");
        sysMsg.set("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");
        JSONArray content = new JSONArray();

        JSONObject textPart = new JSONObject();
        textPart.set("type", "text");
        textPart.set("text", userPrompt);
        content.add(textPart);

        for (File imageFile : imageFiles) {
            JSONObject imagePart = new JSONObject();
            imagePart.set("type", "image_url");
            JSONObject imageUrlObj = new JSONObject();
            imageUrlObj.set("url", "data:image/jpeg;base64," + encodeImageToBase64(imageFile));
            imagePart.set("image_url", imageUrlObj);
            content.add(imagePart);
        }

        userMsg.set("content", content);
        messages.add(userMsg);
        requestBody.set("messages", messages);

        HttpResponse<String> response = sendRequest(requestBody);
        if (response.statusCode() != 200) {
            log.error("Vision JSON API error: {} - {}", response.statusCode(), response.body());
            return null;
        }
        JSONObject respJson = JSONUtil.parseObj(response.body());
        JSONArray choices = respJson.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        String contentStr = choices.getJSONObject(0).getJSONObject("message").getStr("content");
        lastRawResponse.set(contentStr);
        return contentStr;
    }

    private String normalizeJsonObject(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        String jsonStr = content.trim()
                .replaceAll("```[a-zA-Z]*\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
        if (!jsonStr.startsWith("{")) {
            int start = jsonStr.indexOf('{');
            int end = jsonStr.lastIndexOf('}');
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }
        }
        return JSONUtil.parseObj(jsonStr).toString();
    }

    private String normalizeWorkbookResultJson(String content) {
        JSONObject json = JSONUtil.parseObj(normalizeJsonObject(content));
        if (!json.containsKey("totalScore")) {
            json.set("totalScore", 0);
        }
        if (!json.containsKey("overallComment")) {
            json.set("overallComment", "");
        }
        if (!json.containsKey("questions")) {
            json.set("questions", new JSONArray());
        }
        if (!json.containsKey("knowledgePoints")) {
            json.set("knowledgePoints", new JSONArray());
        }
        if (!json.containsKey("annotations")) {
            json.set("annotations", collectQuestionAnnotations(json.getJSONArray("questions")));
        }
        double confidenceScore = clampConfidence(json.getDouble("confidenceScore", 0.78D));
        json.set("confidenceScore", confidenceScore);
        json.set("confidenceLevel", confidenceLevel(confidenceScore));
        json.set("reviewRequired", json.getBool("reviewRequired", false) || confidenceScore < 0.75D);
        return json.toString();
    }

    private JSONArray collectQuestionAnnotations(JSONArray questions) {
        JSONArray annotations = new JSONArray();
        if (questions == null) {
            return annotations;
        }
        for (int i = 0; i < questions.size(); i++) {
            JSONObject question = questions.getJSONObject(i);
            String questionNo = question.getStr("questionNo");
            JSONArray questionAnnotations = question.getJSONArray("annotations");
            if (questionAnnotations == null) {
                JSONObject one = question.getJSONObject("annotation");
                if (one != null) {
                    questionAnnotations = new JSONArray();
                    questionAnnotations.add(one);
                }
            }
            if (questionAnnotations == null) {
                continue;
            }
            for (int j = 0; j < questionAnnotations.size(); j++) {
                JSONObject annotation = questionAnnotations.getJSONObject(j);
                if (annotation != null) {
                    if (!annotation.containsKey("questionNo")) {
                        annotation.set("questionNo", questionNo);
                    }
                    annotations.add(annotation);
                }
            }
        }
        return annotations;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int totalScore(Assignment assignment) {
        return assignment.getTotalScore() == null ? 100 : assignment.getTotalScore();
    }

    private String buildVisionPrompt(Assignment assignment, String subject) {
        String grammarSection = buildSubjectErrorSection(subject);
        grammarSection += "\nImage annotation requirements: return an annotations array when visible mistakes can be located on the uploaded image. Each item must use {pageIndex,type,x,y,width,height,comment,severity,questionNo}. Coordinates are relative numbers from 0 to 1. Use [] when the exact image position is uncertain.\n";
        String knowledgeSection = buildKnowledgeSection(subject);
        return String.format(
                "## 评分任务\n\n" +
                "### 题目要求:\n%s\n\n" +
                "### 参考范文:\n%s\n\n" +
                "### 评分标准:\n%s\n\n" +
                "上面是学生作文的图片，请仔细阅读图片中的作文内容后进行评分。\n\n" +
                "---\n" +
                "请对这篇作文进行评分，只返回以下格式的JSON:\n" +
                "{\n" +
                "  \"totalScore\": <分数, 满分%d>,\n" +
                "  \"overallComment\": \"<总体评价，2-3句话>\",\n" +
                "  \"confidenceScore\": <0到1之间的小数，表示本次批改置信度>,\n" +
                "  \"confidenceLevel\": \"<HIGH/MEDIUM/LOW>\",\n" +
                "  \"reviewRequired\": <置信度低于0.75或存在不确定判断时为true>,\n" +
                "  \"dimensions\": [\n" +
                "    { \"name\": \"<维度名称>\", \"score\": <得分>, \"maxScore\": <满分>, \"comment\": \"<点评>\" }\n" +
                "  ],\n" +
                "  \"grammarErrors\": [\n" +
                "    { \"original\": \"<原文错误片段>\", \"correction\": \"<正确写法>\", \"explanation\": \"<错误原因>\", \"errorType\": \"语法/拼写/逻辑/计算\", \"knowledgePoint\": \"<涉及的知识点>\", \"position\": \"<错误在文中的大致位置，如：第二段第三行>\" }\n" +
                "  ],\n" +
                "  \"knowledgePoints\": [\n" +
                "    { \"topic\": \"<知识点名称>\", \"description\": \"<该知识点在此题中的正确运用>\", \"suggestion\": \"<巩固练习建议>\" }\n" +
                "  ]\n" +
                "}\n" +
                "请严格公正评分。" + grammarSection +
                "对于卷面书写维度，请根据字迹工整度、页面整洁度、段落格式和涂改痕迹来评判。" + knowledgeSection,
                assignment.getQuestion(), assignment.getModelEssay(),
                assignment.getScoringCriteria() != null ? assignment.getScoringCriteria() : getDefaultCriteria(assignment.getTotalScore(), subject),
                assignment.getTotalScore()
        );
    }

    private String buildPrompt(Assignment assignment, String ocrText, String subject) {
        String grammarSection = buildSubjectErrorSection(subject);
        grammarSection += "\nThis OCR-only request has no image position data, so return annotations as an empty array unless exact coordinates are available.\n";
        String knowledgeSection = buildKnowledgeSection(subject);
        return String.format(
                "## 评分任务\n\n" +
                "### 题目要求:\n%s\n\n" +
                "### 参考范文:\n%s\n\n" +
                "### 评分标准:\n%s\n\n" +
                "### 学生作文 (OCR识别文本):\n%s\n\n" +
                "---\n" +
                "请对这篇作文进行评分，只返回以下格式的JSON:\n" +
                "{\n" +
                "  \"totalScore\": <分数, 满分%d>,\n" +
                "  \"overallComment\": \"<总体评价，2-3句话>\",\n" +
                "  \"confidenceScore\": <0到1之间的小数，表示本次批改置信度>,\n" +
                "  \"confidenceLevel\": \"<HIGH/MEDIUM/LOW>\",\n" +
                "  \"reviewRequired\": <置信度低于0.75或存在不确定判断时为true>,\n" +
                "  \"dimensions\": [\n" +
                "    { \"name\": \"<维度名称>\", \"score\": <得分>, \"maxScore\": <满分>, \"comment\": \"<点评>\" }\n" +
                "  ],\n" +
                "  \"grammarErrors\": [\n" +
                "    { \"original\": \"<原文错误片段>\", \"correction\": \"<正确写法>\", \"explanation\": \"<错误原因>\", \"errorType\": \"语法/拼写/逻辑/计算\", \"knowledgePoint\": \"<涉及的知识点>\", \"position\": \"<大致位置描述>\" }\n" +
                "  ],\n" +
                "  \"knowledgePoints\": [\n" +
                "    { \"topic\": \"<知识点名称>\", \"description\": \"<正确运用说明>\", \"suggestion\": \"<巩固建议>\" }\n" +
                "  ]\n" +
                "}\n" +
                "请严格公正评分。" + grammarSection +
                "对于卷面书写维度，请根据字迹工整度、页面整洁度、段落格式和涂改痕迹来评判。" + knowledgeSection,
                assignment.getQuestion(), assignment.getModelEssay(),
                assignment.getScoringCriteria() != null ? assignment.getScoringCriteria() : getDefaultCriteria(assignment.getTotalScore(), subject),
                ocrText, assignment.getTotalScore()
        );
    }

    private String getDefaultCriteria(int totalScore, String subject) {
        int dimCount = 4;
        int dimScore = totalScore / dimCount;
        List<String> dims;
        if ("语文".equals(subject)) {
            dims = List.of("立意与中心", "语言与修辞", "结构与逻辑", "卷面书写");
        } else if ("数学".equals(subject)) {
            dims = List.of("解题思路", "计算过程", "答案正确性", "卷面书写");
        } else {
            dims = List.of("内容要点", "语法与词汇", "结构连贯性", "卷面书写");
        }
        List<Map<String, Object>> dimensionList = new ArrayList<>();
        for (String dim : dims) {
            dimensionList.add(Map.of("name", dim, "max_score", dimScore));
        }
        return JSONUtil.toJsonStr(Map.of(
                "dimensions", dimensionList,
                "total_score", totalScore
        ));
    }

    private String buildSubjectErrorSection(String subject) {
        if ("英语".equals(subject) || subject == null || subject.isEmpty()) {
            return "必须逐一找出学生作文中所有的语法错误、拼写错误、标点错误、用词不当和句式问题，" +
                    "记录在 grammarErrors 数组中（不要遗漏任何错误）。如果没有错误，grammarErrors 为空数组。" +
                    "对于 OCR 误识别、latex 残留、公式符号、错误分词等由识别过程引入的噪声，不计入学生错误，不参与扣分；" +
                    "你需要先自行还原这些 OCR 噪声后的原句，再判断学生真实语法问题。" +
                    "\n大小写判定规则（严格执行）：" +
                    "（1）忽略单词内部的大小写错误，例如 \"wOrd\"、\"tExt\" 不影响分数，也不计入 grammarErrors；" +
                    "（2）句子开头的首字母必须大写：若句首单词首字母未大写，则视为错误并扣分，必须记录到 grammarErrors（errorType 为\"语法\"）；" +
                    "（3）请严格只依据句首字符判断大小写错误，不要因单词内部大小写而误判句首是否正确。";
        } else if ("语文".equals(subject)) {
            return "必须逐一找出学生作文中所有的错别字、病句、标点错误和表达不当，" +
                    "记录在 grammarErrors 数组中（不要遗漏任何错误）。如果没有错误，grammarErrors 为空数组。" +
                    "对于 OCR 误识别等由识别过程引入的噪声，不计入学生错误，不参与扣分；" +
                    "你需要先自行还原 OCR 噪声后的原句，再判断学生真实错误。";
        } else if ("数学".equals(subject)) {
            return "必须逐一检查学生的解题步骤是否正确，找出计算错误、公式运用错误和逻辑漏洞，" +
                    "记录在 grammarErrors 数组中（original 为错误片段，correction 为正确写法，explanation 为错误原因）。" +
                    "如果没有错误，grammarErrors 为空数组。";
        }
        return "记录在 grammarErrors 数组中，如果没有错误则留空。";
    }

    private String buildKnowledgeSection(String subject) {
        return "另外，请从本道题中提取3-5个核心知识点，放入 knowledgePoints 数组。" +
                "每个知识点应包括topic（知识点名称）、description（本文中的正确运用方式）、" +
                "suggestion（建议学生如何巩固该知识点）。" +
                "重点关注本次得分较低维度的相关知识点。";
    }

    private GradingResultDTO parseResult(String content) {
        log.info("Raw AI response (first 300 chars): {}",
                content != null && content.length() > 300 ? content.substring(0, 300) : content);

        String jsonStr = content.trim();
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.replaceAll("```[a-z]*\\s*", "").replaceAll("```\\s*", "");
        }
        if (!jsonStr.startsWith("{")) {
            int start = jsonStr.indexOf('{');
            int end = jsonStr.lastIndexOf('}');
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }
        }

        JSONObject json = JSONUtil.parseObj(jsonStr);

        GradingResultDTO result = new GradingResultDTO();
        result.setTotalScore(new BigDecimal(json.getDouble("totalScore")));
        result.setOverallComment(json.getStr("overallComment"));
        double confidenceScore = clampConfidence(json.getDouble("confidenceScore", 0.78D));
        result.setConfidenceScore(BigDecimal.valueOf(confidenceScore));
        result.setConfidenceLevel(confidenceLevel(confidenceScore));
        result.setReviewRequired(json.getBool("reviewRequired", false) || confidenceScore < 0.75D);

        List<GradingResultDTO.DimensionScore> dimensions = new ArrayList<>();
        JSONArray dims = json.getJSONArray("dimensions");
        if (dims != null) {
            for (int i = 0; i < dims.size(); i++) {
                JSONObject dim = dims.getJSONObject(i);
                GradingResultDTO.DimensionScore ds = new GradingResultDTO.DimensionScore();
                ds.setName(dim.getStr("name"));
                ds.setScore(new BigDecimal(dim.getDouble("score")));
                ds.setMaxScore(new BigDecimal(dim.getDouble("maxScore")));
                ds.setComment(dim.getStr("comment"));
                dimensions.add(ds);
            }
        }
        result.setDimensions(dimensions);

        List<GradingResultDTO.GrammarError> errors = new ArrayList<>();
        JSONArray errs = json.getJSONArray("grammarErrors");
        if (errs != null) {
            for (int i = 0; i < errs.size(); i++) {
                JSONObject err = errs.getJSONObject(i);
                GradingResultDTO.GrammarError ge = new GradingResultDTO.GrammarError();
                ge.setOriginal(err.getStr("original"));
                ge.setCorrection(err.getStr("correction"));
                ge.setExplanation(err.getStr("explanation"));
                ge.setErrorType(err.getStr("errorType"));
                ge.setKnowledgePoint(err.getStr("knowledgePoint"));
                ge.setPosition(err.getStr("position"));
                errors.add(ge);
            }
        }
        result.setGrammarErrors(errors);

        List<GradingResultDTO.KnowledgePoint> kps = new ArrayList<>();
        JSONArray kpArr = json.getJSONArray("knowledgePoints");
        if (kpArr != null) {
            for (int i = 0; i < kpArr.size(); i++) {
                JSONObject kp = kpArr.getJSONObject(i);
                GradingResultDTO.KnowledgePoint pt = new GradingResultDTO.KnowledgePoint();
                pt.setTopic(kp.getStr("topic"));
                pt.setDescription(kp.getStr("description"));
                pt.setSuggestion(kp.getStr("suggestion"));
                kps.add(pt);
            }
        }
        result.setKnowledgePoints(kps);

        List<GradingResultDTO.ImageAnnotation> annotations = new ArrayList<>();
        JSONArray annArr = json.getJSONArray("annotations");
        if (annArr != null) {
            for (int i = 0; i < annArr.size(); i++) {
                JSONObject ann = annArr.getJSONObject(i);
                GradingResultDTO.ImageAnnotation item = new GradingResultDTO.ImageAnnotation();
                item.setPageIndex(ann.getInt("pageIndex", 0));
                item.setType(ann.getStr("type", "rect"));
                item.setX(toBigDecimal(ann.get("x"), BigDecimal.ZERO));
                item.setY(toBigDecimal(ann.get("y"), BigDecimal.ZERO));
                item.setWidth(toBigDecimal(ann.get("width"), new BigDecimal("0.18")));
                item.setHeight(toBigDecimal(ann.get("height"), new BigDecimal("0.08")));
                item.setComment(ann.getStr("comment"));
                item.setSeverity(ann.getStr("severity"));
                item.setQuestionNo(ann.getStr("questionNo"));
                annotations.add(item);
            }
        }
        result.setAnnotations(annotations);
        return result;
    }

    private double clampConfidence(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.78D;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    private String confidenceLevel(double score) {
        if (score >= 0.90D) return "HIGH";
        if (score >= 0.75D) return "MEDIUM";
        return "LOW";
    }

    private BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static final String ROSTER_SYSTEM_PROMPT =
            "你是一个数据提取助手。你的任务是从OCR识别的花名册文字中提取学生姓名列表。" +
            "花名册通常是表格或列表形式，每行一个学生。请忽略表头、标题等非学生数据。" +
            "只返回JSON数组，不要任何解释。";

    private static final String ROSTER_USER_PROMPT =
            "以下是从班级花名册图片OCR识别出的文字，请从中提取所有学生信息。\n" +
            "要求：\n" +
            "1. 每行一个学生，提取姓名(name)、班级(className)、学号(studentNo)\n" +
            "2. 如果OCR文字中没有班级或学号信息，对应字段留空字符串\n" +
            "3. 过滤掉明显不是姓名的行（如页码、表头、空白行）\n" +
            "4. 只返回以下格式的JSON数组，不要markdown代码块：\n" +
            "[{\"name\":\"张三\",\"className\":\"\",\"studentNo\":\"2024001\"},{\"name\":\"李四\",\"className\":\"\",\"studentNo\":\"\"}]";

    /**
     * 将花名册 OCR 文本发送给 AI，解析出结构化的学生名单
     */
    public List<Map<String, String>> parseRoster(String ocrText) {
        String userPrompt = ROSTER_USER_PROMPT + "\n\nOCR文字：\n" + ocrText;

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", model());
            requestBody.set("max_tokens", 16384);
            requestBody.set("temperature", 0);

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.set("role", "system");
            sysMsg.set("content", ROSTER_SYSTEM_PROMPT);
            messages.add(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.set("role", "user");
            userMsg.set("content", userPrompt);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            log.info("Roster parse request, ocrText length: {}", ocrText.length());

            HttpResponse<String> response = sendRequest(requestBody);
            if (response.statusCode() != 200) {
                log.error("Roster parse API error: {} - {}", response.statusCode(), response.body());
                return fallbackParseNames(ocrText);
            }

            String respBody = response.body();
            log.info("Roster parse API response: {}", respBody != null ? respBody : "null");

            JSONObject respJson = JSONUtil.parseObj(respBody);
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("Roster parse API returned no choices");
                return fallbackParseNames(ocrText);
            }

            String content = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            if (content == null || content.trim().isEmpty()) {
                log.error("Roster parse API returned empty content");
                return fallbackParseNames(ocrText);
            }

            log.info("Roster AI content: {}", content);

            List<Map<String, String>> result = extractRosterJson(content);
            if (result != null && !result.isEmpty()) {
                return result;
            }
            return fallbackParseNames(ocrText);
        } catch (Exception e) {
            log.error("Roster parse failed", e);
            return fallbackParseNames(ocrText);
        }
    }

    private List<Map<String, String>> extractRosterJson(String content) {
        try {
            String jsonStr = content.trim();
            // Remove markdown code fences
            jsonStr = jsonStr.replaceAll("```[a-z]*\\s*", "").replaceAll("```\\s*", "").trim();
            // Find JSON array bounds
            int start = jsonStr.indexOf('[');
            int end = jsonStr.lastIndexOf(']');
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }
            JSONArray arr = JSONUtil.parseArray(jsonStr);
            List<Map<String, String>> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getStr("name", "").trim();
                if (name.isEmpty()) continue;
                Map<String, String> student = new java.util.HashMap<>();
                student.put("name", name);
                student.put("className", obj.getStr("className", "").trim());
                student.put("studentNo", obj.getStr("studentNo", "").trim());
                result.add(student);
            }
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            log.warn("JSON extraction failed, content: {}", content, e);
        }
        return null;
    }

    /**
     * 降级方案：按行解析 OCR 文本，每行作为一个学生姓名
     */
    private List<Map<String, String>> fallbackParseNames(String ocrText) {
        log.info("Using fallback name parsing");
        List<Map<String, String>> result = new ArrayList<>();
        String[] lines = ocrText.split("[\\r\\n]+");
        for (String line : lines) {
            String name = line.trim();
            // 过滤明显不是姓名的行
            if (name.isEmpty()) continue;
            if (name.length() > 20) continue; // 太长的行不是姓名
            if (name.matches(".*[a-zA-Z]{4,}.*")) continue; // 含多个英文单词，可能是范文
            if (name.matches("^[\\d\\s\\.\\-、，,。:：()（）#]+$")) continue; // 纯数字符号
            if (name.contains("班级") || name.contains("姓名") || name.contains("学号")
                    || name.contains("花名册") || name.contains("学生") || name.contains("序号")) continue;
            // 去掉序号前缀（如 "1."、"1、"）
            name = name.replaceFirst("^\\d+[\\.、\\s]+", "").trim();
            if (name.isEmpty() || name.length() > 20) continue;
            Map<String, String> student = new java.util.HashMap<>();
            student.put("name", name);
            student.put("className", "");
            student.put("studentNo", "");
            result.add(student);
        }
        log.info("Fallback parsed {} names", result.size());
        return result;
    }

    private String encodeImageToBase64(File imageFile) throws IOException {
        File compressed = compressForVision(imageFile);
        byte[] bytes = Files.readAllBytes(compressed != null ? compressed.toPath() : imageFile.toPath());
        String b64 = Base64.getEncoder().encodeToString(bytes);
        if (compressed != null && compressed.exists()) {
            compressed.delete();
        }
        return b64;
    }

    private String encodeImageToBase64Raw(File imageFile) throws IOException {
        byte[] bytes = Files.readAllBytes(imageFile.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 检测 OCR 文本是否实际可用（非纯图片链接、非空）
     */
    public static boolean isOcrTextUsable(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) return false;
        String cleaned = ocrText.trim()
                .replaceAll("!\\[.*?\\]\\(https?://[^\\)]+\\)", "")
                .trim();
        return cleaned.length() >= 10;
    }

    /**
     * 视觉方案：将花名册图片发给视觉模型解析姓名+学号
     */
    public List<Map<String, String>> parseRosterVision(File imageFile) {
        try {
            String base64Image = encodeImageToBase64Raw(imageFile);

            JSONObject requestBody = new JSONObject();
            requestBody.set("model", visionModel());
            requestBody.set("max_tokens", 16384);
            requestBody.set("temperature", 0);

            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.set("role", "system");
            sysMsg.set("content", "你是一个教育数据提取助手。你的任务是从学生花名册图片中提取所有学生的姓名和学号。只返回JSON数组，不要任何解释。");
            messages.add(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.set("role", "user");
            JSONArray content = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.set("type", "text");
            textPart.set("text",
                    "请从这张花名册图片中提取所有学生的姓名和学号。" +
                    "以JSON数组返回，格式：[{\"name\":\"姓名\",\"studentNo\":\"学号\"}]。" +
                    "如果没有学号，studentNo留空字符串。");
            content.add(textPart);

            JSONObject imagePart = new JSONObject();
            imagePart.set("type", "image_url");
            JSONObject imageUrlObj = new JSONObject();
            imageUrlObj.set("url", "data:image/jpeg;base64," + base64Image);
            imagePart.set("image_url", imageUrlObj);
            content.add(imagePart);

            userMsg.set("content", content);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            log.info("Vision roster request, image raw bytes={}, base64 length={}",
                    imageFile.length(), base64Image.length());

            HttpResponse<String> response = sendRequest(requestBody);
            if (response.statusCode() != 200) {
                log.error("Vision roster API error: {} - {}", response.statusCode(), response.body());
                return null;
            }

            String respBody = response.body();
            log.info("Vision roster response: {}", respBody);

            JSONObject respJson = JSONUtil.parseObj(respBody);
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("Vision roster: no choices");
                return null;
            }

            String respContent = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            if (respContent == null || respContent.trim().isEmpty()) {
                log.error("Vision roster: empty content");
                return null;
            }

            log.info("Vision roster content: {}", respContent);
            return extractRosterJson(respContent);
        } catch (Exception e) {
            log.error("Vision roster parse failed", e);
            return null;
        }
    }

    private File compressForVision(File original) {
        try {
            BufferedImage image = ImageIO.read(original);
            if (image == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();
            int maxSide = 1024;
            if (width > maxSide || height > maxSide) {
                double ratio = Math.min((double) maxSide / width, (double) maxSide / height);
                width = (int) (width * ratio);
                height = (int) (height * ratio);
                BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                resized.createGraphics().drawImage(
                        image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
                image = resized;
            }

            File compressed = new File(original.getParent(), "vision_" + original.getName());
            javax.imageio.IIOImage iioImage = new javax.imageio.IIOImage(image, null, null);
            javax.imageio.ImageWriteParam param = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next().getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.7f);
            try (FileOutputStream fos = new FileOutputStream(compressed);
                 javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(fos)) {
                javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
                writer.setOutput(ios);
                writer.write(null, iioImage, param);
                writer.dispose();
            }
            log.info("Vision image compressed: {}x{}, {} bytes", width, height, compressed.length());
            return compressed;
        } catch (Exception e) {
            log.warn("Image compress for vision failed, using original", e);
            return null;
        }
    }
}
