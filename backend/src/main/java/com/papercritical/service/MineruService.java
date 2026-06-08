package com.papercritical.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU document parsing service.
 *
 * Flow:
 * 1. Create a batch parsing task and get a signed upload URL.
 * 2. Upload the local image to the signed URL.
 * 3. Poll the batch result.
 * 4. Download full.md and structured JSON files from the result zip.
 */
@Service
public class MineruService {

    private static final Logger log = LoggerFactory.getLogger(MineruService.class);
    private static final int MAX_POLL_SECONDS = 180;
    private static final int POLL_INTERVAL_MS = 3000;
    private static final int MAX_ANALYSIS_CHARS = 26000;
    private static final int MAX_MARKDOWN_CHARS = 16000;
    private static final int MAX_STRUCTURE_SUMMARY_CHARS = 10000;
    private static final int MAX_STRUCTURE_ITEMS = 180;

    @Value("${mineru.api-key:}")
    private String apiKey;

    @Value("${mineru.base-url:https://mineru.net/api/v4}")
    private String baseUrl;

    @Value("${mineru.model-version:pipeline}")
    private String modelVersion;

    private final ThreadLocal<String> lastError = new ThreadLocal<>();

    public String extractText(File imageFile) {
        ParseResult result = parse(imageFile);
        return result == null ? null : result.toAnalysisText();
    }

    public String getLastError() {
        return StrUtil.nullToEmpty(lastError.get());
    }

    public ParseResult mergeResults(List<ParseResult> results) {
        if (results == null || results.isEmpty()) {
            return ParseResult.empty("");
        }
        StringBuilder markdown = new StringBuilder();
        JSONArray pages = new JSONArray();
        int pageIndex = 0;
        for (ParseResult result : results) {
            if (result == null || !result.hasContent()) {
                continue;
            }
            if (StrUtil.isNotBlank(result.getMarkdown())) {
                markdown.append("## Page ").append(pageIndex + 1).append("\n")
                        .append(result.getMarkdown().trim()).append("\n\n");
            }
            JSONObject page = new JSONObject();
            page.set("pageIndex", pageIndex);
            page.set("batchId", result.getBatchId());
            page.set("markdownLength", result.getMarkdown() == null ? 0 : result.getMarkdown().length());
            page.set("structure", parseJsonOrText(result.getStructureJson()));
            pages.add(page);
            pageIndex++;
        }
        JSONObject structure = new JSONObject();
        structure.set("source", "mineru_multi_page");
        structure.set("pages", pages);
        return new ParseResult(markdown.toString(), structure.toString());
    }

    public ParseResult parse(File imageFile) {
        lastError.remove();
        try {
            if (StrUtil.isBlank(apiKey)) {
                setLastError("MinerU api key is empty. Please set MINERU_API_KEY.");
                log.error(getLastError());
                return null;
            }
            if (imageFile == null || !imageFile.exists()) {
                setLastError("MinerU input file does not exist: " + imageFile);
                log.error(getLastError());
                return null;
            }

            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            String effectiveModel = StrUtil.blankToDefault(modelVersion, "pipeline").trim();

            log.info("MinerU applying upload url: file={}, bytes={}, model={}",
                    imageFile.getName(), imageBytes.length, effectiveModel);

            HttpResponse createResp = applyUploadUrl(imageFile, effectiveModel);
            JSONObject createResult = parseCreateResult(createResp);
            if (createResult == null) {
                return null;
            }
            if (createResult.getInt("code", -1) != 0) {
                if (isUnsupportedVersion(createResult) && !"pipeline".equalsIgnoreCase(effectiveModel)) {
                    log.warn("MinerU model version '{}' is not supported, retrying with pipeline", effectiveModel);
                    createResp = applyUploadUrl(imageFile, "pipeline");
                    createResult = parseCreateResult(createResp);
                    if (createResult == null) {
                        return null;
                    }
                }
            }
            if (createResult.getInt("code", -1) != 0) {
                setLastError("MinerU apply upload url error: code="
                        + createResult.getInt("code", -1)
                        + ", msg=" + createResult.getStr("msg", ""));
                log.error("MinerU apply upload url error: code={}, msg={}",
                        createResult.getInt("code", -1), createResult.getStr("msg", ""));
                return null;
            }

            JSONObject createData = createResult.getJSONObject("data");
            String batchId = createData == null ? null : createData.getStr("batch_id");
            JSONArray fileUrls = createData == null ? null : createData.getJSONArray("file_urls");
            if (StrUtil.isBlank(batchId) || fileUrls == null || fileUrls.isEmpty() || StrUtil.isBlank(fileUrls.getStr(0))) {
                setLastError("MinerU response missing batch id or upload url.");
                log.error("{} body={}", getLastError(), createResp.body());
                return null;
            }

            if (!uploadToSignedUrl(fileUrls.getStr(0), imageBytes)) {
                setLastError("MinerU file upload failed, batchId=" + batchId);
                log.error(getLastError());
                return null;
            }

            log.info("MinerU file uploaded, batchId={}", batchId);
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(MAX_POLL_SECONDS);
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_MS);

                HttpResponse pollResp = HttpRequest.get(apiBaseUrl() + "/extract-results/batch/" + batchId)
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(30000)
                        .execute();

                if (pollResp.getStatus() != 200) {
                    log.warn("MinerU poll failed: status={}, retrying", pollResp.getStatus());
                    continue;
                }

                JSONObject pollResult = JSONUtil.parseObj(pollResp.body());
                if (pollResult.getInt("code", -1) != 0) {
                    log.warn("MinerU poll error: code={}, msg={}",
                            pollResult.getInt("code", -1), pollResult.getStr("msg", ""));
                    continue;
                }

                JSONObject pollData = pollResult.getJSONObject("data");
                JSONObject extractResult = firstExtractResult(pollData);
                if (extractResult == null) {
                    log.warn("MinerU poll response missing extract_result: {}", pollResp.body());
                    continue;
                }

                String status = extractResult.getStr("state", extractResult.getStr("status", ""));
                log.info("MinerU task status: batchId={}, status={}", batchId, status);

                if ("done".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
                    ParseResult result = downloadParseResult(extractResult);
                    if (result == null || !result.hasContent()) {
                        setLastError("MinerU parse result is empty, batchId=" + batchId);
                        log.warn(getLastError());
                        return ParseResult.empty(batchId);
                    }
                    result.setBatchId(batchId);
                    log.info("MinerU parsing completed, markdown length={} chars, structure length={} chars",
                            lengthOf(result.getMarkdown()), lengthOf(result.getStructureJson()));
                    return result;
                }

                if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                    String errMsg = extractResult.getStr("err_msg",
                            extractResult.getStr("error_msg", "Unknown MinerU error"));
                    setLastError("MinerU task failed: " + errMsg);
                    log.error("MinerU task failed: status={}, error={}", status, errMsg);
                    return null;
                }
            }

            setLastError("MinerU task timed out after " + MAX_POLL_SECONDS + "s, batchId=" + batchId);
            log.error(getLastError());
            return null;
        } catch (Exception e) {
            setLastError("MinerU extract failed: " + e.getMessage());
            log.error("MinerU extract failed", e);
            return null;
        }
    }

    private HttpResponse applyUploadUrl(File imageFile, String effectiveModel) {
        JSONObject fileItem = new JSONObject();
        fileItem.set("name", imageFile.getName());
        fileItem.set("data_id", "essay-" + System.currentTimeMillis());

        JSONArray files = new JSONArray();
        files.add(fileItem);

        JSONObject taskData = new JSONObject();
        taskData.set("files", files);
        if (StrUtil.isNotBlank(effectiveModel)) {
            taskData.set("model_version", effectiveModel);
        }

        return HttpRequest.post(apiBaseUrl() + "/file-urls/batch")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(60000)
                .body(taskData.toString())
                .execute();
    }

    private JSONObject parseCreateResult(HttpResponse createResp) {
        if (createResp.getStatus() != 200) {
            setLastError("MinerU apply upload url failed: status=" + createResp.getStatus());
            log.error("{} body={}", getLastError(), createResp.body());
            return null;
        }
        return JSONUtil.parseObj(createResp.body());
    }

    private boolean isUnsupportedVersion(JSONObject createResult) {
        String msg = createResult == null ? "" : StrUtil.nullToEmpty(createResult.getStr("msg", "")).toLowerCase();
        return msg.contains("version") && (msg.contains("invalid") || msg.contains("not supported"));
    }

    private void setLastError(String message) {
        lastError.set(StrUtil.nullToEmpty(message));
    }

    private String apiBaseUrl() {
        String value = StrUtil.blankToDefault(baseUrl, "https://mineru.net/api/v4").trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean uploadToSignedUrl(String uploadUrl, byte[] bytes) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.getOutputStream().write(bytes);
            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            log.error("MinerU signed upload failed: status={}, body={}", status, readStream(conn.getErrorStream()));
            return false;
        } catch (Exception e) {
            log.error("MinerU signed upload exception", e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private JSONObject firstExtractResult(JSONObject pollData) {
        if (pollData == null) return null;
        Object result = pollData.get("extract_result");
        if (result instanceof JSONArray) {
            JSONArray array = (JSONArray) result;
            return array.isEmpty() ? null : array.getJSONObject(0);
        }
        if (result instanceof JSONObject) {
            return (JSONObject) result;
        }
        return pollData;
    }

    private ParseResult downloadParseResult(JSONObject extractResult) throws IOException {
        JSONObject structure = new JSONObject();
        structure.set("extractResult", extractResult);
        String fullZipUrl = extractResult.getStr("full_zip_url");
        if (StrUtil.isNotBlank(fullZipUrl)) {
            return downloadResultFromZip(fullZipUrl, extractResult);
        }

        String markdownUrl = extractResult.getStr("markdown_url");
        if (StrUtil.isNotBlank(markdownUrl)) {
            String markdown = HttpRequest.get(markdownUrl).timeout(30000).execute().body();
            structure.set("source", "markdown_url");
            return new ParseResult(markdown, structure.toString());
        }

        if (StrUtil.isBlank(fullZipUrl)) {
            log.error("MinerU done but no markdown_url/full_zip_url found: {}", extractResult);
            return null;
        }
        return null;
    }

    private ParseResult downloadResultFromZip(String zipUrl, JSONObject extractResult) throws IOException {
        HttpResponse response = HttpRequest.get(zipUrl).timeout(60000).execute();
        if (response.getStatus() != 200) {
            log.error("MinerU zip download failed: status={}", response.getStatus());
            return null;
        }
        String markdown = "";
        JSONObject files = new JSONObject();
        JSONObject structure = new JSONObject();
        structure.set("extractResult", extractResult);
        structure.set("source", "full_zip_url");

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.bodyBytes()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.endsWith("/full.md") || "full.md".equals(name)) {
                    markdown = readStream(zip);
                    continue;
                }
                if (isUsefulJson(name)) {
                    String jsonText = readStream(zip);
                    files.set(name, parseJsonOrText(jsonText));
                }
            }
        }
        structure.set("files", files);
        if (StrUtil.isBlank(markdown) && files.isEmpty()) {
            log.error("MinerU zip downloaded but no full.md or structured JSON was found: {}", zipUrl);
            return null;
        }
        return new ParseResult(markdown, structure.toString());
    }

    private boolean isUsefulJson(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        return lower.endsWith(".json")
                && (lower.contains("content_list")
                || lower.contains("middle")
                || lower.contains("model"));
    }

    private Object parseJsonOrText(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        try {
            String trimmed = value.trim();
            if (trimmed.startsWith("[")) {
                return JSONUtil.parseArray(trimmed);
            }
            if (trimmed.startsWith("{")) {
                return JSONUtil.parseObj(trimmed);
            }
        } catch (Exception ignored) {
        }
        return value;
    }

    private String readStream(InputStream input) throws IOException {
        if (input == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = input.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n...[truncated, original chars=" + value.length() + "]";
    }

    private static void summarizeStructure(Object node, StringBuilder sb, int depth, int[] count) {
        if (node == null || depth > 8 || count[0] >= MAX_STRUCTURE_ITEMS
                || sb.length() >= MAX_STRUCTURE_SUMMARY_CHARS) {
            return;
        }
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            appendStructureLine(obj, sb, count);
            String[] childKeys = {
                    "pages", "files", "content", "children", "blocks", "lines", "spans",
                    "paragraphs", "items", "cells", "tables", "equations"
            };
            for (String key : childKeys) {
                Object child = obj.get(key);
                if (child instanceof JSONArray || child instanceof JSONObject) {
                    summarizeStructure(child, sb, depth + 1, count);
                }
                if (count[0] >= MAX_STRUCTURE_ITEMS || sb.length() >= MAX_STRUCTURE_SUMMARY_CHARS) {
                    return;
                }
            }
            if (depth < 4) {
                for (String key : obj.keySet()) {
                    Object child = obj.get(key);
                    if (child instanceof JSONArray || child instanceof JSONObject) {
                        summarizeStructure(child, sb, depth + 1, count);
                    }
                    if (count[0] >= MAX_STRUCTURE_ITEMS || sb.length() >= MAX_STRUCTURE_SUMMARY_CHARS) {
                        return;
                    }
                }
            }
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            int limit = Math.min(arr.size(), 120);
            for (int i = 0; i < limit; i++) {
                summarizeStructure(arr.get(i), sb, depth + 1, count);
                if (count[0] >= MAX_STRUCTURE_ITEMS || sb.length() >= MAX_STRUCTURE_SUMMARY_CHARS) {
                    return;
                }
            }
            return;
        }
        if (node instanceof CharSequence) {
            String text = cleanText(String.valueOf(node));
            if (StrUtil.isNotBlank(text) && !looksLikeUrl(text)) {
                sb.append("- ").append(truncate(text, 300)).append('\n');
                count[0]++;
            }
        }
    }

    private static void appendStructureLine(JSONObject obj, StringBuilder sb, int[] count) {
        String text = firstText(obj, "text", "content", "table_body", "latex", "html", "title", "value");
        if (StrUtil.isBlank(text) || looksLikeUrl(text)) {
            return;
        }
        String type = firstText(obj, "type", "category");
        String page = firstText(obj, "page_idx", "page_no", "pageIndex", "page");
        Object bbox = obj.get("bbox");
        sb.append("- ");
        if (StrUtil.isNotBlank(type)) {
            sb.append('[').append(type).append(']');
        }
        if (StrUtil.isNotBlank(page)) {
            sb.append("[page=").append(page).append(']');
        }
        if (bbox != null) {
            sb.append("[bbox=").append(truncate(String.valueOf(bbox), 120)).append(']');
        }
        sb.append(' ').append(truncate(cleanText(text), 500)).append('\n');
        count[0]++;
    }

    private static String firstText(JSONObject obj, String... keys) {
        for (String key : keys) {
            Object value = obj.get(key);
            if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
                String text = cleanText(String.valueOf(value));
                if (StrUtil.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private static boolean looksLikeUrl(String text) {
        String value = text == null ? "" : text.trim().toLowerCase();
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:image/");
    }

    public static class ParseResult {
        private String batchId;
        private final String markdown;
        private final String structureJson;

        public ParseResult(String markdown, String structureJson) {
            this.markdown = markdown == null ? "" : markdown;
            this.structureJson = structureJson == null ? "" : structureJson;
        }

        static ParseResult empty(String batchId) {
            ParseResult result = new ParseResult("", "");
            result.setBatchId(batchId);
            return result;
        }

        public boolean hasContent() {
            return StrUtil.isNotBlank(markdown) || StrUtil.isNotBlank(structureJson);
        }

        public String toAnalysisText() {
            StringBuilder sb = new StringBuilder();
            if (StrUtil.isNotBlank(markdown)) {
                sb.append("## MinerU Markdown\n").append(truncate(markdown.trim(), MAX_MARKDOWN_CHARS)).append("\n");
            }
            String structureSummary = toStructureSummary();
            if (StrUtil.isNotBlank(structureSummary)) {
                sb.append("\n## MinerU Structure Summary\n").append(structureSummary);
            }
            return truncate(sb.toString().trim(), MAX_ANALYSIS_CHARS);
        }

        public String toStructureSummary() {
            if (StrUtil.isBlank(structureJson)) {
                return "";
            }
            try {
                Object parsed = parseStructuredJson(structureJson);
                StringBuilder sb = new StringBuilder();
                if (StrUtil.isNotBlank(batchId)) {
                    sb.append("batchId=").append(batchId).append('\n');
                }
                summarizeStructure(parsed, sb, 0, new int[]{0});
                return truncate(sb.toString().trim(), MAX_STRUCTURE_SUMMARY_CHARS);
            } catch (Exception e) {
                return truncate(structureJson.trim(), 3000);
            }
        }

        private static Object parseStructuredJson(String value) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.startsWith("[")) {
                return JSONUtil.parseArray(trimmed);
            }
            return JSONUtil.parseObj(trimmed);
        }

        public String getBatchId() {
            return batchId;
        }

        public void setBatchId(String batchId) {
            this.batchId = batchId;
        }

        public String getMarkdown() {
            return markdown;
        }

        public String getStructureJson() {
            return structureJson;
        }
    }
}
