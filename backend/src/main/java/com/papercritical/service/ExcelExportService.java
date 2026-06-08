package com.papercritical.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.papercritical.entity.Assignment;
import com.papercritical.entity.Essay;
import com.papercritical.entity.Student;
import com.papercritical.mapper.AssignmentMapper;
import com.papercritical.mapper.EssayMapper;
import com.papercritical.mapper.StudentMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelExportService {

    private final EssayMapper essayMapper;
    private final AssignmentMapper assignmentMapper;
    private final StudentMapper studentMapper;

    public ExcelExportService(EssayMapper essayMapper, AssignmentMapper assignmentMapper,
                              StudentMapper studentMapper) {
        this.essayMapper = essayMapper;
        this.assignmentMapper = assignmentMapper;
        this.studentMapper = studentMapper;
    }

    public byte[] exportEssays(Long assignmentId, Long teacherId, String assignmentTitle) throws IOException {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !teacherId.equals(assignment.getTeacherId())) {
            throw new IllegalArgumentException("作业不存在或无权导出");
        }
        List<Essay> essays = essayMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Essay>()
                        .eq(Essay::getAssignmentId, assignmentId)
                        .in(Essay::getStatus, "GRADED", "TEACHER_REVIEWED")
                        .orderByAsc(Essay::getStudentId));

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle warnStyle = createWarnStyle(workbook);
            CellStyle goodStyle = createGoodStyle(workbook);

            // ---- Sheet1: 成绩明细 ----
            Sheet sheet = workbook.createSheet("成绩明细");

            List<String> dimNames = extractDimNames(essays);
            List<String> headers = new ArrayList<>(List.of("序号", "学生姓名", "总得分", "等第"));
            headers.addAll(dimNames);
            headers.addAll(List.of("错误数", "薄弱知识点", "总评"));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i < 4 || i >= headers.size() - 3 ? 3200 : 3600);
            }
            sheet.setColumnWidth(headers.size() - 3, 8000);
            sheet.setColumnWidth(headers.size() - 2, 6000);

            int rowIdx = 1;
            for (Essay e : essays) {
                JSONObject fb = parseFeedback(e.getAiFeedback());
                Row row = sheet.createRow(rowIdx);
                row.createCell(0).setCellValue(rowIdx);
                row.createCell(1).setCellValue(studentName(e));
                BigDecimal score = effectiveScore(e);
                row.createCell(2).setCellValue(score.doubleValue());
                row.createCell(3).setCellValue(scoreGrade(score, assignment.getTotalScore()));

                for (int d = 0; d < dimNames.size(); d++) {
                    double[] dimInfo = getDimensionScore(fb, dimNames.get(d), assignment.getTotalScore());
                    Cell dimCell = row.createCell(4 + d);
                    dimCell.setCellValue(dimInfo[0]);
                    dimCell.setCellStyle(dimInfo[1] < 0.5 ? warnStyle :
                            dimInfo[1] >= 0.85 ? goodStyle : dataStyle);
                }

                int errCount = countErrors(fb);
                String kpSummary = summarizeKnowledgePoints(fb);
                String comment = fb != null ? fb.getStr("overallComment", "") : "";

                row.createCell(4 + dimNames.size()).setCellValue(errCount);
                row.createCell(5 + dimNames.size()).setCellValue(kpSummary);
                row.createCell(6 + dimNames.size()).setCellValue(comment);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    if (cell == null) continue;
                    if (cell.getCellStyle() == headerStyle) continue;
                    if (cell.getCellStyle() != warnStyle && cell.getCellStyle() != goodStyle) {
                        cell.setCellStyle(dataStyle);
                    }
                }
                rowIdx++;
            }

            double avgScore = essays.stream()
                    .map(this::effectiveScore)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average().orElse(0);

            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(0).setCellValue("");
            totalRow.createCell(1).setCellValue("共" + essays.size() + "人");
            totalRow.createCell(2).setCellValue(round1(avgScore));

            // ---- Sheet2: 薄弱点分析 ----
            Sheet analysisSheet = workbook.createSheet("薄弱点分析");
            Row ahRow = analysisSheet.createRow(0);
            String[] ah = {"薄弱知识点", "错误人数", "错误占比", "涉及学生"};
            for (int i = 0; i < ah.length; i++) {
                Cell cell = ahRow.createCell(i);
                cell.setCellValue(ah[i]);
                cell.setCellStyle(headerStyle);
            }
            analysisSheet.setColumnWidth(0, 6000);
            analysisSheet.setColumnWidth(3, 12000);

            Map<String, List<String>> kpStudents = new LinkedHashMap<>();
            for (Essay e : essays) {
                JSONObject fb = parseFeedback(e.getAiFeedback());
                Set<String> kps = extractKnowledgePoints(fb);
                for (String kp : kps) {
                    kpStudents.computeIfAbsent(kp, k -> new ArrayList<>()).add(studentName(e));
                }
            }

            List<Map.Entry<String, List<String>>> sorted = kpStudents.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .collect(Collectors.toList());

            int ar = 1;
            for (Map.Entry<String, List<String>> entry : sorted) {
                Row row = analysisSheet.createRow(ar);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue().size());
                row.createCell(2).setCellValue(round1(entry.getValue().size() * 100.0 / essays.size()) + "%");
                row.createCell(3).setCellValue(String.join("、", entry.getValue()));
                for (int i = 0; i < 4; i++) row.getCell(i).setCellStyle(dataStyle);
                ar++;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private List<String> extractDimNames(List<Essay> essays) {
        for (Essay e : essays) {
            JSONObject fb = parseFeedback(e.getAiFeedback());
            JSONArray dims = fb != null ? fb.getJSONArray("dimensions") : null;
            if (dims != null && !dims.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < dims.size(); i++) {
                    names.add(dims.getJSONObject(i).getStr("name", "维度" + (i + 1)));
                }
                return names;
            }
        }
        return List.of("内容要点", "语法词汇", "结构连贯", "卷面书写");
    }

    private double[] getDimensionScore(JSONObject fb, String dimName, int totalScore) {
        if (fb == null) return new double[]{0, 0};
        JSONArray dims = fb.getJSONArray("dimensions");
        if (dims == null) return new double[]{0, 0};
        for (int i = 0; i < dims.size(); i++) {
            JSONObject d = dims.getJSONObject(i);
            if (dimName.equals(d.getStr("name"))) {
                double score = d.getDouble("score", 0.0);
                double max = d.getDouble("maxScore", 1.0);
                return new double[]{score, max > 0 ? score / max : 0};
            }
        }
        return new double[]{0, 0};
    }

    private int countErrors(JSONObject fb) {
        if (fb == null) return 0;
        JSONArray errs = fb.getJSONArray("grammarErrors");
        return errs != null ? errs.size() : 0;
    }

    private Set<String> extractKnowledgePoints(JSONObject fb) {
        Set<String> kps = new LinkedHashSet<>();
        if (fb == null) return kps;
        JSONArray errs = fb.getJSONArray("grammarErrors");
        if (errs != null) {
            for (int i = 0; i < errs.size(); i++) {
                String kp = errs.getJSONObject(i).getStr("knowledgePoint", "");
                if (!kp.isEmpty()) kps.add(kp);
            }
        }
        JSONArray kpArr = fb.getJSONArray("knowledgePoints");
        if (kpArr != null) {
            for (int i = 0; i < kpArr.size(); i++) {
                String topic = kpArr.getJSONObject(i).getStr("topic", "");
                if (!topic.isEmpty()) kps.add(topic);
            }
        }
        return kps;
    }

    private String summarizeKnowledgePoints(JSONObject fb) {
        Set<String> kps = extractKnowledgePoints(fb);
        return kps.isEmpty() ? "" : String.join("、", kps);
    }

    private JSONObject parseFeedback(String aiFeedback) {
        if (aiFeedback == null || aiFeedback.trim().isEmpty()) return null;
        try {
            return JSONUtil.parseObj(aiFeedback);
        } catch (Exception e) {
            return null;
        }
    }

    private String scoreGrade(BigDecimal score, int totalScore) {
        if (score == null || totalScore <= 0) return "";
        double pct = score.doubleValue() / totalScore;
        if (pct >= 0.9) return "A";
        if (pct >= 0.75) return "B";
        if (pct >= 0.6) return "C";
        return "D";
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createWarnStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createGoodStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private BigDecimal effectiveScore(Essay essay) {
        if (essay.getFinalScore() != null && essay.getFinalScore().compareTo(BigDecimal.ZERO) > 0) {
            return essay.getFinalScore();
        }
        if (essay.getAiScore() != null) {
            return essay.getAiScore();
        }
        return BigDecimal.ZERO;
    }

    private String studentName(Essay essay) {
        Student student = studentMapper.selectById(essay.getStudentId());
        if (student != null && student.getName() != null && !student.getName().trim().isEmpty()) {
            return student.getName();
        }
        return "学生ID: " + essay.getStudentId();
    }

    private double round1(double v) {
        return new BigDecimal(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
