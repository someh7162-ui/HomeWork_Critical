package com.papercritical.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    @Value("${ocr.app-id}")
    private String appId;

    @Value("${ocr.app-secret}")
    private String appSecret;

    @Value("${ocr.api-url}")
    private String apiUrl;

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 作文 OCR：不指定 rec_mode 优先（保留英文单词间距），逐级降级
     */
    public String recognize(File imageFile) {
        // 1: 压缩图 + 不指定 rec_mode（自动检测，保留空格）
        String result = tryRecognize(imageFile, apiUrl, true, null);
        if (StrUtil.isNotBlank(result)) return result;

        // 2: 原图 + 不指定 rec_mode
        log.info("Compressed+auto empty, retrying with raw image...");
        result = tryRecognize(imageFile, apiUrl, false, null);
        if (StrUtil.isNotBlank(result)) return result;

        // 3: 压缩图 + rec_mode=general
        log.info("Still empty, trying rec_mode=general...");
        result = tryRecognize(imageFile, apiUrl, true, "general");
        if (StrUtil.isNotBlank(result)) return result;

        // 4: doc_ocr 兜底
        String docOcrUrl = apiUrl.replace("simpletex_ocr", "doc_ocr");
        log.info("Trying doc_ocr fallback...");
        result = tryRecognize(imageFile, docOcrUrl, true, null);
        if (StrUtil.isNotBlank(result)) return result;

        log.error("All OCR attempts failed");
        return null;
    }

    /**
     * 花名册专用 OCR：多种策略尝试提取文字
     * 策略1: 不指定 rec_mode 原始图 → 2: 不指定 rec_mode 压缩图 →
     * 3: rec_mode=general → 4: rec_mode=document（若返回图片URL则下载后重OCR）
     */
    public String recognizeRoster(File imageFile) {
        String result;

        // 策略1：不指定 rec_mode，原始图
        result = tryRecognize(imageFile, apiUrl, false, null);
        if (hasUsableText(result)) return result;
        log.info("Roster OCR strategy 1 (no rec_mode, raw) no usable text");

        // 策略2：不指定 rec_mode，压缩图
        result = tryRecognize(imageFile, apiUrl, true, null);
        if (hasUsableText(result)) return result;
        log.info("Roster OCR strategy 2 (no rec_mode, compressed) no usable text");

        // 策略3：rec_mode=general
        result = tryRecognize(imageFile, apiUrl, false, "general");
        if (hasUsableText(result)) return result;
        log.info("Roster OCR strategy 3 (rec_mode=general) no usable text");

        // 策略4：rec_mode=document，若返回图片URL则下载后重OCR
        result = tryRecognize(imageFile, apiUrl, false, "document");
        if (hasUsableText(result)) return result;

        // 策略5：如果 rec_mode=document 返回了图片URL，下载该图片重新OCR
        String imageUrl = extractImageUrl(result);
        if (imageUrl != null) {
            log.info("Roster OCR strategy 4 returned image URL, downloading and re-OCRing: {}", imageUrl);
            File downloaded = downloadImage(imageUrl, imageFile.getParentFile());
            if (downloaded != null) {
                result = tryRecognize(downloaded, apiUrl, false, null);
                downloaded.delete();
                if (hasUsableText(result)) return result;
                log.info("Roster OCR strategy 5 (download+reOCR) no usable text");
            }
        }

        log.error("All roster OCR strategies failed to produce usable text");
        return null;
    }

    /**
     * 检查 OCR 结果是否包含可用的文字（非纯图片链接）
     */
    private boolean hasUsableText(String ocrResult) {
        if (StrUtil.isBlank(ocrResult)) return false;
        String cleaned = ocrResult.trim()
                .replaceAll("!\\[.*?\\]\\(https?://[^\\)]+\\)", "")
                .replaceAll("\\s+", "")
                .trim();
        return cleaned.length() >= 10;
    }

    /**
     * 从 OCR 结果中提取 SimpleTex 图片 URL
     */
    private String extractImageUrl(String ocrResult) {
        if (ocrResult == null) return null;
        Matcher m = Pattern.compile("!\\[.*?\\]\\((https?://[^\\)]+)\\)").matcher(ocrResult);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * 下载远程图片到本地
     */
    private File downloadImage(String imageUrl, File parentDir) {
        try {
            URL url = new URL(imageUrl);
            File tmp = new File(parentDir, "downloaded_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = url.openStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Downloaded SimpleTex rendered image: {} bytes", tmp.length());
            return tmp;
        } catch (Exception e) {
            log.warn("Failed to download SimpleTex image: {}", e.getMessage());
            return null;
        }
    }

    private String tryRecognize(File imageFile, String url, boolean compress, String recMode) {
        File compressedFile = null;
        try {
            File uploadFile = imageFile;
            if (compress) {
                compressedFile = compressImage(imageFile);
                if (compressedFile != null) {
                    uploadFile = compressedFile;
                }
            }

            int maxRetries = 5;
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                String randomStr = generateRandomStr(16);
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

                Map<String, String> signMap = new TreeMap<>();
                signMap.put("app-id", appId);
                signMap.put("random-str", randomStr);
                if (recMode != null) {
                    signMap.put("rec_mode", recMode);
                }
                signMap.put("timestamp", timestamp);

                StringBuilder signStr = new StringBuilder();
                for (Map.Entry<String, String> entry : signMap.entrySet()) {
                    if (signStr.length() > 0) signStr.append("&");
                    signStr.append(entry.getKey()).append("=").append(entry.getValue());
                }
                signStr.append("&secret=").append(appSecret);

                String sign = DigestUtil.md5Hex(signStr.toString());
                log.info("OCR request (attempt {}): {} compress={}, recMode={}, size={}",
                        attempt + 1, url, compress, recMode, uploadFile.length());

                HttpRequest httpReq = HttpRequest.post(url)
                        .header("app-id", appId)
                        .header("random-str", randomStr)
                        .header("timestamp", timestamp)
                        .header("sign", sign)
                        .form("file", uploadFile)
                        .timeout(30000)
                        .setReadTimeout(120000);
                if (recMode != null) {
                    httpReq.form("rec_mode", recMode);
                }
                HttpResponse response = httpReq.execute();

                String responseBody = response.body();
                int status = response.getStatus();

                log.info("OCR response: status={}, len={}", status,
                        responseBody != null ? responseBody.length() : 0);

                if (status == 200) {
                    return parseOcrResponse(responseBody);
                }

                if (status == 429 && attempt < maxRetries - 1) {
                    long waitMs = (long) (3000 * Math.pow(2, attempt)) + (long) (Math.random() * 1000);
                    log.warn("OCR rate limited (429), retrying in {}ms...", waitMs);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    continue;
                }

                log.error("OCR API error: status={}", status);
                return null;
            }
            return null;
        } catch (Exception e) {
            log.error("OCR call failed for {}: {}", url, e.getMessage());
            return null;
        } finally {
            if (compressedFile != null && compressedFile.exists()) {
                compressedFile.delete();
            }
        }
    }

    /**
     * 清理 SimpleTex OCR 结果中的 LaTeX 标记和数学符号
     */
    private String cleanLatexMarkup(String text) {
        if (text == null) return null;
        // 1. 移除 $$ 和 $ 包裹（保留内容）
        text = text.replaceAll("\\$\\$(.+?)\\$\\$", "$1");
        text = text.replaceAll("(?<!\\\\)\\$(.+?)(?<!\\\\)\\$", "$1");
        // 2. 移除 \(\) 和 \[\] 包裹
        text = text.replaceAll("\\\\\\((.+?)\\\\\\)", "$1");
        text = text.replaceAll("\\\\\\[(.+?)\\\\\\]", "$1");
        // 3. 移除 \begin{...}...\end{...} 环境（保留内容）
        text = text.replaceAll("\\\\begin\\{[^}]+\\}(.+?)\\\\end\\{[^}]+\\}", "$1");
        // 4. 移除 \text{...} \textbf{...} \textit{...} 等（保留内容）
        text = text.replaceAll("\\\\text\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\textbf\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\textit\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\underline\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\overline\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\mathrm\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\mathbf\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\mathtt\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\mathit\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\mathsf\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\\\mathtt\\{(.+?)\\}", "$1");
        // 5. 移除 \frac{a}{b} 多参数命令 → 保留 a/b
        text = text.replaceAll("\\\\frac\\{(.+?)\\}\\{(.+?)\\}", "$1/$2");
        // 6. 移除 _{...} 和 ^{...} 上下标
        text = text.replaceAll("_\\{(.+?)\\}", "$1");
        text = text.replaceAll("\\^\\{(.+?)\\}", "$1");
        // 7. 移除常见 LaTeX 数学符号和命令
        text = text.replaceAll("\\\\[a-zA-Z]+", "");
        // 8. 清理残留的 { } 括号
        text = text.replaceAll("[{}]", "");
        // 9. 保留段落换行，只清理行内多余空格和空行
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll(" *\\n *", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.trim();
        // 10. 过滤 OCR 噪声行
        return filterNoiseLines(text);
    }

    /**
     * 过滤 OCR 噪声行：过短、纯符号、无实际英文单词的行
     */
    private String filterNoiseLines(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] lines = text.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (sb.length() > 0 && !sb.toString().endsWith("\n\n")) {
                    sb.append("\n");
                }
                continue;
            }
            // 过滤条件
            int letters = 0;
            int digits = 0;
            int symbols = 0;
            for (char c : trimmed.toCharArray()) {
                if (Character.isLetter(c)) letters++;
                else if (Character.isDigit(c)) digits++;
                else if (!Character.isWhitespace(c)) symbols++;
            }
            int total = letters + digits + symbols;
            if (total == 0) continue;
            double letterRatio = (double) letters / total;
            double symbolRatio = (double) symbols / total;
            // 纯符号行（如 =====, -----, /10/）
            if (letters == 0 && digits <= 3) continue;
            // 短噪声行：< 8 个字符且字母占比 < 30%（如 LN/10/, G_0=1）
            if (total < 8 && letterRatio < 0.3) continue;
            // 高符号比例行：符号 > 50% 且字母 < 5（如 -1{2}=1{2}=...）
            if (symbolRatio > 0.5 && letters < 5) continue;
            sb.append(trimmed).append("\n");
        }
        return sb.toString().trim();
    }

    private String parseOcrResponse(String responseBody) {
        JSONObject json = JSONUtil.parseObj(responseBody);

        JSONObject res = json.getJSONObject("res");
        if (res == null) {
            // 兼容 { "data": { "text": "..." } } 格式
            JSONObject data = json.getJSONObject("data");
            if (data != null) {
                String text = data.getStr("text");
                if (StrUtil.isNotBlank(text)) return cleanLatexMarkup(text);
            }
            String text = json.getStr("text");
            if (StrUtil.isNotBlank(text)) return cleanLatexMarkup(text);
            log.warn("No 'res' field in OCR response");
            return null;
        }

        String type = res.getStr("type", "unknown");
        log.info("OCR response type: {}", type);

        // info 可能是 JSONObject（有 markdown）或空字符串
        Object infoObj = res.get("info");
        if (infoObj instanceof JSONObject) {
            JSONObject info = (JSONObject) infoObj;
            String markdown = info.getStr("markdown");
            if (StrUtil.isNotBlank(markdown)) {
                return cleanLatexMarkup(markdown);
            }
            String text = info.getStr("text");
            if (StrUtil.isNotBlank(text)) {
                return cleanLatexMarkup(text);
            }
        }

        // info 为空字符串或不存在 -> formula 类型等情况，无文本可提取
        log.warn("OCR returned type={}, info is empty or not an object", type);
        return null;
    }

    private String generateRandomStr(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt((int) (Math.random() * CHARS.length())));
        }
        return sb.toString();
    }

    private File compressImage(File original) {
        try {
            BufferedImage image = ImageIO.read(original);
            if (image == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();
            int maxSide = 2048;
            if (width > maxSide || height > maxSide) {
                double ratio = Math.min((double) maxSide / width, (double) maxSide / height);
                width = (int) (width * ratio);
                height = (int) (height * ratio);
                BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                resized.createGraphics().drawImage(
                        image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
                image = resized;
            }

            File compressed = new File(original.getParent(), "compressed_" + original.getName());
            try (FileOutputStream fos = new FileOutputStream(compressed)) {
                ImageIO.write(image, "jpg", fos);
            }
            log.info("Image compressed: {} -> {} bytes ({}x{})", original.length(), compressed.length(), width, height);
            return compressed;
        } catch (Exception e) {
            log.warn("Image compression failed, using original", e);
            return null;
        }
    }
}
