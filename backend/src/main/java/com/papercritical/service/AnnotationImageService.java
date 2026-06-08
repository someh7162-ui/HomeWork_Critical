package com.papercritical.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.papercritical.entity.Essay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnnotationImageService {

    private static final Logger log = LoggerFactory.getLogger(AnnotationImageService.class);
    private static final int MAX_NOTES_PER_PAGE = 8;

    @Value("${upload.path}")
    private String uploadPath;

    public RenderResult renderForEssay(Essay essay, String feedbackJson) {
        if (essay == null || isBlank(feedbackJson)) {
            return RenderResult.empty();
        }
        List<String> imageUrls = parseImageUrls(essay);
        if (imageUrls.isEmpty()) {
            return RenderResult.empty();
        }
        List<AnnotationItem> annotations = collectAnnotations(feedbackJson);
        if (annotations.isEmpty()) {
            return RenderResult.empty();
        }

        List<String> renderedUrls = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String sourceUrl = imageUrls.get(i);
            File source = resolveImagePath(sourceUrl).toFile();
            if (!source.exists()) {
                log.warn("Annotation source image does not exist: {}", source.getAbsolutePath());
                continue;
            }
            List<AnnotationItem> pageAnnotations = annotationsForPage(annotations, i);
            if (pageAnnotations.isEmpty()) {
                continue;
            }
            String renderedUrl = renderOne(source, pageAnnotations);
            if (!isBlank(renderedUrl)) {
                renderedUrls.add(renderedUrl);
            }
        }
        return new RenderResult(renderedUrls);
    }

    private String renderOne(File source, List<AnnotationItem> annotations) {
        try {
            BufferedImage original = ImageIO.read(source);
            if (original == null) {
                return null;
            }
            int width = original.getWidth();
            int height = original.getHeight();
            int baseFontSize = Math.max(22, Math.min(42, width / 34));
            int noteFontSize = Math.max(20, Math.min(34, width / 42));
            int footerHeight = footerHeight(width, annotations, noteFontSize);

            BufferedImage canvas = new BufferedImage(width, height + footerHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            setupGraphics(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            g.drawImage(original, 0, 0, null);

            Font labelFont = pickFont(Font.BOLD, baseFontSize);
            g.setFont(labelFont);
            for (int i = 0; i < annotations.size(); i++) {
                drawAnnotation(g, annotations.get(i), i + 1, width, height, labelFont);
            }
            drawFooter(g, annotations, width, height, footerHeight, noteFontSize);
            g.dispose();

            Path dest = nextOutputPath();
            Files.createDirectories(dest.getParent());
            ImageIO.write(canvas, "jpg", dest.toFile());
            return toRelativeUploadUrl(dest);
        } catch (Exception e) {
            log.warn("Failed to render annotated image: {}", source.getAbsolutePath(), e);
            return null;
        }
    }

    private void drawAnnotation(Graphics2D g, AnnotationItem ann, int index, int width, int height, Font labelFont) {
        Color color = severityColor(ann.severity);
        int strokeWidth = Math.max(5, width / 220);
        int labelSize = Math.max(32, width / 28);
        boolean pin = "pin".equalsIgnoreCase(ann.type);
        int x = (int) Math.round(clamp(ann.x, 0, 1) * width);
        int y = (int) Math.round(clamp(ann.y, 0, 1) * height);
        int w = Math.max(labelSize, (int) Math.round(clamp(ann.width, 0.035, 1) * width));
        int h = Math.max(labelSize, (int) Math.round(clamp(ann.height, 0.03, 1) * height));

        g.setStroke(new BasicStroke(strokeWidth));
        if (pin) {
            int cx = clampInt(x, labelSize / 2, width - labelSize / 2);
            int cy = clampInt(y, labelSize / 2, height - labelSize / 2);
            g.setColor(new Color(255, 255, 255, 230));
            g.fillOval(cx - labelSize / 2 - 4, cy - labelSize / 2 - 4, labelSize + 8, labelSize + 8);
            g.setColor(color);
            g.fillOval(cx - labelSize / 2, cy - labelSize / 2, labelSize, labelSize);
            drawCenteredLabel(g, String.valueOf(index), cx, cy, labelFont);
            return;
        }

        x = clampInt(x, 0, Math.max(0, width - w));
        y = clampInt(y, 0, Math.max(0, height - h));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(color);
        g.drawRoundRect(x, y, w, h, 10, 10);

        int labelX = clampInt(x - labelSize / 2, 0, Math.max(0, width - labelSize));
        int labelY = clampInt(y - labelSize / 2, 0, Math.max(0, height - labelSize));
        g.fillOval(labelX, labelY, labelSize, labelSize);
        drawCenteredLabel(g, String.valueOf(index), labelX + labelSize / 2, labelY + labelSize / 2, labelFont);
    }

    private void drawCenteredLabel(Graphics2D g, String label, int centerX, int centerY, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int textX = centerX - fm.stringWidth(label) / 2;
        int textY = centerY + (fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(Color.WHITE);
        g.drawString(label, textX, textY);
    }

    private void drawFooter(Graphics2D g, List<AnnotationItem> annotations, int width, int imageHeight,
                            int footerHeight, int noteFontSize) {
        if (footerHeight <= 0) {
            return;
        }
        g.setColor(new Color(255, 250, 250));
        g.fillRect(0, imageHeight, width, footerHeight);
        g.setColor(new Color(254, 202, 202));
        g.setStroke(new BasicStroke(Math.max(2, width / 500)));
        g.drawLine(0, imageHeight, width, imageHeight);

        Font noteFont = pickFont(Font.PLAIN, noteFontSize);
        Font indexFont = pickFont(Font.BOLD, noteFontSize);
        int margin = Math.max(20, width / 42);
        int lineHeight = Math.max(noteFontSize + 10, width / 46);
        int y = imageHeight + margin + lineHeight - 8;
        int textWidth = width - margin * 2 - lineHeight * 2;
        int limit = Math.min(MAX_NOTES_PER_PAGE, annotations.size());

        for (int i = 0; i < limit; i++) {
            AnnotationItem ann = annotations.get(i);
            Color color = severityColor(ann.severity);
            int badge = lineHeight + 2;
            int badgeX = margin;
            int badgeY = y - lineHeight + 3;
            g.setColor(color);
            g.fillOval(badgeX, badgeY, badge, badge);
            g.setFont(indexFont);
            drawCenteredLabel(g, String.valueOf(i + 1), badgeX + badge / 2, badgeY + badge / 2, indexFont);

            g.setFont(noteFont);
            g.setColor(new Color(55, 65, 81));
            String note = compactNote(ann);
            List<String> lines = wrap(note, g.getFontMetrics(), textWidth);
            for (String line : lines) {
                g.drawString(line, margin + badge + 12, y);
                y += lineHeight;
            }
            y += Math.max(4, lineHeight / 5);
        }
        if (annotations.size() > limit) {
            g.setFont(noteFont);
            g.setColor(new Color(107, 114, 128));
            g.drawString("还有 " + (annotations.size() - limit) + " 处批注，详见逐题结果。", margin, y);
        }
    }

    private int footerHeight(int width, List<AnnotationItem> annotations, int noteFontSize) {
        int margin = Math.max(20, width / 42);
        int lineHeight = Math.max(noteFontSize + 10, width / 46);
        int limit = Math.min(MAX_NOTES_PER_PAGE, annotations.size());
        int approxCharsPerLine = Math.max(18, width / Math.max(18, noteFontSize));
        int lines = 0;
        for (int i = 0; i < limit; i++) {
            int len = compactNote(annotations.get(i)).length();
            lines += Math.max(1, (int) Math.ceil(len / (double) approxCharsPerLine));
        }
        int extra = annotations.size() > limit ? lineHeight : 0;
        int maxFooter = Math.max(180, (int) (width * 0.45));
        int footer = margin * 2 + lines * lineHeight + limit * Math.max(4, lineHeight / 5) + extra;
        return Math.min(Math.max(footer, margin * 3 + lineHeight), maxFooter);
    }

    private List<AnnotationItem> collectAnnotations(String feedbackJson) {
        try {
            JSONObject source = JSONUtil.parseObj(feedbackJson);
            List<AnnotationItem> list = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            JSONArray top = source.getJSONArray("annotations");
            if (top != null) {
                for (int i = 0; i < top.size(); i++) {
                    addAnnotation(list, seen, top.getJSONObject(i), null);
                }
            }

            JSONArray questions = source.getJSONArray("questions");
            if (questions != null) {
                for (int i = 0; i < questions.size(); i++) {
                    JSONObject question = questions.getJSONObject(i);
                    JSONObject fallback = new JSONObject();
                    fallback.set("questionNo", question.getStr("questionNo"));
                    fallback.set("comment", firstText(question.getStr("feedback"), question.getStr("knowledgePoint")));
                    JSONArray anns = question.getJSONArray("annotations");
                    if (anns != null) {
                        for (int j = 0; j < anns.size(); j++) {
                            addAnnotation(list, seen, anns.getJSONObject(j), fallback);
                        }
                    } else {
                        JSONObject one = question.getJSONObject("annotation");
                        if (one != null) {
                            addAnnotation(list, seen, one, fallback);
                        }
                    }
                }
                if (list.isEmpty()) {
                    addQuestionFallbacks(list, seen, questions);
                }
            }

            JSONArray errors = source.getJSONArray("grammarErrors");
            if (list.isEmpty() && errors != null) {
                for (int i = 0; i < Math.min(errors.size(), MAX_NOTES_PER_PAGE); i++) {
                    JSONObject err = errors.getJSONObject(i);
                    JSONObject ann = new JSONObject();
                    ann.set("type", "pin");
                    ann.set("x", 0.06D);
                    ann.set("y", Math.min(0.08D + i * 0.11D, 0.86D));
                    ann.set("width", 0.05D);
                    ann.set("height", 0.05D);
                    ann.set("severity", "error");
                    ann.set("comment", firstText(
                            combineOriginalAndCorrection(err),
                            err.getStr("explanation"),
                            err.getStr("knowledgePoint")));
                    addAnnotation(list, seen, ann, null);
                }
            }

            JSONArray dimensions = source.getJSONArray("dimensions");
            if (list.isEmpty() && dimensions != null) {
                for (int i = 0; i < dimensions.size() && list.size() < 6; i++) {
                    JSONObject dim = dimensions.getJSONObject(i);
                    double score = number(dim.get("score"), 0D);
                    double max = number(dim.get("maxScore"), 0D);
                    if (max > 0 && score < max) {
                        JSONObject ann = new JSONObject();
                        ann.set("type", "pin");
                        ann.set("x", 0.08D);
                        ann.set("y", Math.min(0.12D + list.size() * 0.12D, 0.84D));
                        ann.set("width", 0.05D);
                        ann.set("height", 0.05D);
                        ann.set("severity", "warning");
                        ann.set("comment", firstText(dim.getStr("name") + ": " + dim.getStr("comment"), dim.getStr("comment")));
                        addAnnotation(list, seen, ann, null);
                    }
                }
            }

            if (list.isEmpty() && !isBlank(source.getStr("overallComment"))) {
                JSONObject ann = new JSONObject();
                ann.set("type", "pin");
                ann.set("x", 0.08D);
                ann.set("y", 0.12D);
                ann.set("width", 0.05D);
                ann.set("height", 0.05D);
                ann.set("severity", "info");
                ann.set("comment", source.getStr("overallComment"));
                addAnnotation(list, seen, ann, null);
            }
            return list;
        } catch (Exception e) {
            log.warn("Failed to parse annotation JSON", e);
            return new ArrayList<>();
        }
    }

    private void addQuestionFallbacks(List<AnnotationItem> list, Set<String> seen, JSONArray questions) {
        for (int i = 0; i < questions.size() && list.size() < MAX_NOTES_PER_PAGE; i++) {
            JSONObject question = questions.getJSONObject(i);
            double score = number(question.get("score"), 0D);
            double max = number(question.get("maxScore"), 0D);
            boolean wrong = Boolean.FALSE.equals(question.getBool("isCorrect")) || (max > 0 && score < max);
            if (!wrong) {
                continue;
            }
            JSONObject ann = new JSONObject();
            ann.set("type", "pin");
            ann.set("x", 0.08D);
            ann.set("y", Math.min(0.10D + list.size() * 0.10D, 0.86D));
            ann.set("width", 0.05D);
            ann.set("height", 0.05D);
            ann.set("severity", "error");
            ann.set("questionNo", question.getStr("questionNo"));
            ann.set("comment", firstText(question.getStr("feedback"), question.getStr("knowledgePoint"), "该题建议订正"));
            addAnnotation(list, seen, ann, null);
        }
    }

    private void addAnnotation(List<AnnotationItem> list, Set<String> seen, JSONObject raw, JSONObject fallback) {
        if (raw == null) {
            return;
        }
        AnnotationItem item = new AnnotationItem();
        item.pageIndex = (int) number(firstValue(raw.get("pageIndex"), fallback == null ? null : fallback.get("pageIndex")), 0D);
        item.type = firstText(raw.getStr("type"), "rect");
        item.x = clamp(number(raw.get("x"), 0.08D), 0D, 1D);
        item.y = clamp(number(raw.get("y"), 0.12D), 0D, 1D);
        item.width = clamp(number(firstValue(raw.get("width"), raw.get("w")), 0.18D), 0.035D, 1D);
        item.height = clamp(number(firstValue(raw.get("height"), raw.get("h")), 0.08D), 0.03D, 1D);
        item.comment = firstText(raw.getStr("comment"), raw.getStr("feedback"),
                fallback == null ? null : fallback.getStr("comment"), raw.getStr("questionNo"), "请查看该处批注");
        item.severity = firstText(raw.getStr("severity"), raw.getStr("type"), "error");
        item.questionNo = firstText(raw.getStr("questionNo"), fallback == null ? null : fallback.getStr("questionNo"));
        String key = item.pageIndex + "|" + item.type + "|" + item.x + "|" + item.y + "|" + item.width + "|" + item.height + "|" + item.comment;
        if (seen.add(key)) {
            list.add(item);
        }
    }

    private List<AnnotationItem> annotationsForPage(List<AnnotationItem> annotations, int pageIndex) {
        List<AnnotationItem> rows = new ArrayList<>();
        for (AnnotationItem item : annotations) {
            if (item.pageIndex == pageIndex) {
                rows.add(item);
            }
        }
        return rows;
    }

    private List<String> parseImageUrls(Essay essay) {
        List<String> urls = new ArrayList<>();
        if (!isBlank(essay.getImageUrls())) {
            try {
                JSONArray arr = JSONUtil.parseArray(essay.getImageUrls());
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

    private Path resolveImagePath(String imageUrl) {
        Path basePath = baseUploadPath();
        List<String> candidates = imagePathCandidates(imageUrl);
        Path firstSafe = null;
        for (String candidate : candidates) {
            Path resolved = safeResolve(basePath, candidate);
            if (resolved == null) {
                continue;
            }
            if (firstSafe == null) {
                firstSafe = resolved;
            }
            if (Files.exists(resolved)) {
                return resolved;
            }
        }
        return firstSafe == null ? basePath.resolve(cleanRelativeUploadUrl(imageUrl)).normalize() : firstSafe;
    }

    private Path nextOutputPath() {
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return baseUploadPath()
                .resolve("annotated")
                .resolve(datePath)
                .resolve(UUID.randomUUID() + ".jpg")
                .normalize();
    }

    private Path baseUploadPath() {
        Path basePath = Paths.get(uploadPath);
        if (!basePath.isAbsolute()) {
            basePath = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
        }
        return basePath.normalize();
    }

    private String toRelativeUploadUrl(Path dest) {
        return baseUploadPath().relativize(dest).toString().replace(File.separatorChar, '/');
    }

    private String cleanRelativeUploadUrl(String imageUrl) {
        String value = imageUrl == null ? "" : imageUrl.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.startsWith("uploads/essays/")) {
            value = value.substring("uploads/essays/".length());
        } else if (value.startsWith("uploads/")) {
            value = value.substring("uploads/".length());
        }
        if (value.startsWith("essays/")) {
            value = value.substring("essays/".length());
        }
        return value;
    }

    private List<String> imagePathCandidates(String imageUrl) {
        List<String> candidates = new ArrayList<>();
        String value = imageUrl == null ? "" : imageUrl.trim().replace('\\', '/');
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }

        addCandidate(candidates, value);
        String withoutUploads = value.startsWith("uploads/") ? value.substring("uploads/".length()) : value;
        addCandidate(candidates, withoutUploads);
        String withoutEssays = withoutUploads.startsWith("essays/")
                ? withoutUploads.substring("essays/".length())
                : withoutUploads;
        addCandidate(candidates, withoutEssays);
        if (!withoutEssays.startsWith("essays/")) {
            addCandidate(candidates, "essays/" + withoutEssays);
        }
        addCandidate(candidates, cleanRelativeUploadUrl(imageUrl));
        return candidates;
    }

    private void addCandidate(List<String> candidates, String value) {
        if (!isBlank(value) && !candidates.contains(value)) {
            candidates.add(value);
        }
    }

    private Path safeResolve(Path basePath, String relativePath) {
        if (isBlank(relativePath)) {
            return null;
        }
        Path resolved = basePath.resolve(relativePath).normalize();
        return resolved.startsWith(basePath) ? resolved : null;
    }

    private void setupGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private Font pickFont(int style, int size) {
        String[] names = {
                "Microsoft YaHei",
                "SimSun",
                "Noto Sans SC",
                "Noto Sans CJK SC",
                "Source Han Sans SC",
                "WenQuanYi Micro Hei",
                "Droid Sans Fallback",
                "SansSerif"
        };
        for (String name : names) {
            Font font = new Font(name, style, size);
            if (font.canDisplay('批')) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            String next = line.toString() + ch;
            if (fm.stringWidth(next) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            line.append(ch);
            if (lines.size() >= 3) {
                break;
            }
        }
        if (line.length() > 0 && lines.size() < 4) {
            lines.add(line.toString());
        }
        return lines;
    }

    private Color severityColor(String severity) {
        if ("warning".equalsIgnoreCase(severity)) {
            return new Color(245, 158, 11);
        }
        if ("info".equalsIgnoreCase(severity) || "note".equalsIgnoreCase(severity)) {
            return new Color(37, 99, 235);
        }
        return new Color(239, 68, 68);
    }

    private String compactNote(AnnotationItem ann) {
        String prefix = isBlank(ann.questionNo) ? "" : "第" + ann.questionNo + "题：";
        String note = prefix + firstText(ann.comment, "请查看该处批注");
        note = note.replaceAll("\\s+", " ").trim();
        return note.length() > 90 ? note.substring(0, 90) + "..." : note;
    }

    private String combineOriginalAndCorrection(JSONObject err) {
        if (err == null) {
            return "";
        }
        String original = err.getStr("original");
        String correction = err.getStr("correction");
        if (!isBlank(original) && !isBlank(correction)) {
            return original + " -> " + correction;
        }
        return firstText(original, correction);
    }

    private Object firstValue(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private double number(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    private int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.min(Math.max(value, min), max);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class RenderResult {
        private final List<String> imageUrls;

        RenderResult(List<String> imageUrls) {
            this.imageUrls = imageUrls == null ? new ArrayList<>() : imageUrls;
        }

        static RenderResult empty() {
            return new RenderResult(new ArrayList<>());
        }

        public boolean hasImages() {
            return !imageUrls.isEmpty();
        }

        public String firstImageUrl() {
            return imageUrls.isEmpty() ? "" : imageUrls.get(0);
        }

        public List<String> getImageUrls() {
            return imageUrls;
        }
    }

    private static class AnnotationItem {
        int pageIndex;
        String type;
        double x;
        double y;
        double width;
        double height;
        String comment;
        String severity;
        String questionNo;
    }
}
