package com.papercritical.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.entity.Assignment;
import com.papercritical.entity.Essay;
import com.papercritical.entity.Student;
import com.papercritical.mapper.AssignmentMapper;
import com.papercritical.mapper.EssayMapper;
import com.papercritical.mapper.StudentMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private static final List<String> PROCESSING_STATUSES =
            Arrays.asList("UPLOADED", "OCR_PROCESSING", "OCR_DONE", "AI_PROCESSING");

    private final EssayMapper essayMapper;
    private final AssignmentMapper assignmentMapper;
    private final StudentMapper studentMapper;

    public StatsService(EssayMapper essayMapper, AssignmentMapper assignmentMapper,
                        StudentMapper studentMapper) {
        this.essayMapper = essayMapper;
        this.assignmentMapper = assignmentMapper;
        this.studentMapper = studentMapper;
    }

    public Map<String, Object> getAssignmentStats(Long assignmentId, Long teacherId) {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !teacherId.equals(assignment.getTeacherId())) {
            throw new IllegalArgumentException("作业不存在或无权查看");
        }

        List<Essay> allSubmissions = essayMapper.selectList(
                new LambdaQueryWrapper<Essay>()
                        .eq(Essay::getAssignmentId, assignmentId)
                        .orderByDesc(Essay::getCreatedAt));
        List<Essay> latestSubmissions = new ArrayList<>(latestByStudent(allSubmissions).values());
        List<Essay> graded = latestSubmissions.stream()
                .filter(this::isGraded)
                .collect(Collectors.toList());

        long totalStudents = countStudents(assignment, teacherId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assignmentTitle", assignment.getTitle());
        stats.put("assignmentType", typeOf(assignment));
        stats.put("totalScore", totalScoreOf(assignment));
        stats.put("totalEssays", graded.size());
        stats.put("submittedCount", latestSubmissions.size());
        stats.put("unsubmittedCount", Math.max(totalStudents - latestSubmissions.size(), 0));
        stats.put("processingCount", latestSubmissions.stream().filter(this::isProcessing).count());
        stats.put("reviewRequiredCount", latestSubmissions.stream().filter(this::needsReview).count());
        stats.put("confirmedCount", latestSubmissions.stream().filter(e -> "TEACHER_REVIEWED".equals(e.getStatus())).count());
        stats.put("failedCount", latestSubmissions.stream().filter(e -> "FAILED".equals(e.getStatus())).count());

        if (graded.isEmpty()) {
            stats.put("averageScore", 0);
            stats.put("highestScore", 0);
            stats.put("lowestScore", 0);
            stats.put("distribution", Collections.emptyMap());
            stats.put("scores", Collections.emptyList());
            stats.put("knowledgePoints", Collections.emptyList());
            return stats;
        }

        List<BigDecimal> scores = graded.stream().map(this::effectiveScore).collect(Collectors.toList());
        double avg = scores.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double max = scores.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double min = scores.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        int totalScore = totalScoreOf(assignment);

        stats.put("averageScore", round(avg));
        stats.put("highestScore", round(max));
        stats.put("lowestScore", round(min));
        stats.put("distribution", scoreDistribution(scores, totalScore));

        List<Map<String, Object>> scoreList = new ArrayList<>();
        for (Essay essay : graded) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentName", studentName(essay));
            item.put("score", effectiveScore(essay));
            item.put("aiScore", essay.getAiScore());
            item.put("finalScore", essay.getFinalScore());
            scoreList.add(item);
        }
        stats.put("scores", scoreList);
        stats.put("knowledgePoints", collectKnowledgePoints(graded));
        return stats;
    }

    public Map<String, Object> getTeacherInsightDashboard(Long teacherId) {
        List<Assignment> assignments = assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getTeacherId, teacherId)
                .orderByDesc(Assignment::getCreatedAt));
        long studentCount = studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getTeacherId, teacherId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentCount", studentCount);
        data.put("assignmentCount", assignments.size());

        if (assignments.isEmpty()) {
            data.put("submittedCount", 0);
            data.put("gradedCount", 0);
            data.put("processingCount", 0);
            data.put("reviewRequiredCount", 0);
            data.put("averageScore", 0);
            data.put("submitRate", 0);
            data.put("recentAssignments", Collections.emptyList());
            data.put("weakKnowledgePoints", Collections.emptyList());
            data.put("lessonRecommendations", buildLessonRecommendations(Collections.emptyList(), assignments));
            return data;
        }

        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).collect(Collectors.toList());
        List<Essay> submissions = essayMapper.selectList(new LambdaQueryWrapper<Essay>()
                .in(Essay::getAssignmentId, assignmentIds)
                .orderByDesc(Essay::getCreatedAt));
        List<Essay> latest = new ArrayList<>(latestByAssignmentStudent(submissions).values());
        List<Essay> graded = latest.stream().filter(this::isGraded).collect(Collectors.toList());
        long expectedSubmissions = assignments.stream().mapToLong(a -> countStudents(a, teacherId)).sum();
        double avg = graded.stream().map(this::effectiveScore).mapToDouble(BigDecimal::doubleValue).average().orElse(0);

        data.put("submittedCount", latest.size());
        data.put("gradedCount", graded.size());
        data.put("processingCount", latest.stream().filter(this::isProcessing).count());
        data.put("reviewRequiredCount", latest.stream().filter(this::needsReview).count());
        data.put("averageScore", round(avg));
        data.put("submitRate", expectedSubmissions == 0 ? 0 : round(latest.size() * 100.0 / expectedSubmissions));
        data.put("recentAssignments", buildRecentAssignmentRows(assignments, latest, teacherId));
        List<Map<String, Object>> weakPoints = collectKnowledgePoints(graded);
        data.put("weakKnowledgePoints", weakPoints);
        data.put("lessonRecommendations", buildLessonRecommendations(weakPoints, assignments));
        return data;
    }

    private Map<Long, Essay> latestByStudent(List<Essay> submissions) {
        Map<Long, Essay> latest = new LinkedHashMap<>();
        for (Essay essay : submissions) {
            latest.putIfAbsent(essay.getStudentId(), essay);
        }
        return latest;
    }

    private Map<String, Essay> latestByAssignmentStudent(List<Essay> submissions) {
        Map<String, Essay> latest = new LinkedHashMap<>();
        for (Essay essay : submissions) {
            latest.putIfAbsent(essay.getAssignmentId() + ":" + essay.getStudentId(), essay);
        }
        return latest;
    }

    private List<Map<String, Object>> buildRecentAssignmentRows(List<Assignment> assignments, List<Essay> latest, Long teacherId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Assignment assignment : assignments.stream().limit(8).collect(Collectors.toList())) {
            List<Essay> assignmentSubmissions = latest.stream()
                    .filter(e -> assignment.getId().equals(e.getAssignmentId()))
                    .collect(Collectors.toList());
            List<Essay> graded = assignmentSubmissions.stream().filter(this::isGraded).collect(Collectors.toList());
            long expected = countStudents(assignment, teacherId);
            double avg = graded.stream().map(this::effectiveScore).mapToDouble(BigDecimal::doubleValue).average().orElse(0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", assignment.getId());
            row.put("title", assignment.getTitle());
            row.put("subject", assignment.getSubject());
            row.put("className", assignment.getClassName());
            row.put("assignmentType", typeOf(assignment));
            row.put("submittedCount", assignmentSubmissions.size());
            row.put("expectedCount", expected);
            row.put("gradedCount", graded.size());
            row.put("processingCount", assignmentSubmissions.stream().filter(this::isProcessing).count());
            row.put("reviewRequiredCount", assignmentSubmissions.stream().filter(this::needsReview).count());
            row.put("averageScore", round(avg));
            row.put("submitRate", expected == 0 ? 0 : round(assignmentSubmissions.size() * 100.0 / expected));
            rows.add(row);
        }
        return rows;
    }

    private long countStudents(Assignment assignment, Long teacherId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getTeacherId, teacherId);
        if (!isBlank(assignment.getClassName())) {
            wrapper.eq(Student::getClassName, assignment.getClassName());
        }
        return studentMapper.selectCount(wrapper);
    }

    private List<Map<String, Object>> collectKnowledgePoints(List<Essay> essays) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Essay essay : essays) {
            String feedback = !isBlank(essay.getWorkbookResultJson()) ? essay.getWorkbookResultJson() : essay.getAiFeedback();
            if (isBlank(feedback)) {
                continue;
            }
            try {
                JSONObject result = JSONUtil.parseObj(feedback);
                mergeKnowledgePoints(byName, result);
            } catch (Exception ignored) {
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(byName.values());
        rows.sort((a, b) -> Integer.compare((Integer) b.get("wrongCount"), (Integer) a.get("wrongCount")));
        for (Map<String, Object> row : rows) {
            int wrong = (Integer) row.get("wrongCount");
            int total = Math.max((Integer) row.get("totalCount"), 1);
            row.put("wrongRate", round(wrong * 100.0 / total));
        }
        return rows;
    }

    private void mergeKnowledgePoints(Map<String, Map<String, Object>> byName, JSONObject result) {
        JSONArray points = result.getJSONArray("knowledgePoints");
        if (points != null) {
            for (int i = 0; i < points.size(); i++) {
                JSONObject point = points.getJSONObject(i);
                String name = firstText(point.getStr("name"), point.getStr("topic"));
                int wrong = point.getInt("wrongCount", 0);
                int total = point.getInt("totalCount", 1);
                if (wrong == 0 && total <= 1) {
                    wrong = 1;
                }
                mergePoint(byName, name, wrong, Math.max(total, wrong), point.getStr("suggestion"));
            }
        }

        JSONArray questions = result.getJSONArray("questions");
        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                JSONObject question = questions.getJSONObject(i);
                String name = question.getStr("knowledgePoint", "未分类知识点");
                boolean wrong = !question.getBool("isCorrect", false);
                mergePoint(byName, name, wrong ? 1 : 0, 1, question.getStr("feedback"));
            }
        }

        JSONArray errors = result.getJSONArray("grammarErrors");
        if (errors != null) {
            for (int i = 0; i < errors.size(); i++) {
                JSONObject error = errors.getJSONObject(i);
                mergePoint(byName, error.getStr("knowledgePoint"), 1, 1, error.getStr("explanation"));
            }
        }
    }

    private void mergePoint(Map<String, Map<String, Object>> byName, String rawName, int wrongCount, int totalCount, String suggestion) {
        String name = isBlank(rawName) ? "未分类知识点" : rawName.trim();
        Map<String, Object> row = byName.computeIfAbsent(name, key -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("name", key);
            created.put("wrongCount", 0);
            created.put("totalCount", 0);
            created.put("suggestion", "");
            return created;
        });
        row.put("wrongCount", (Integer) row.get("wrongCount") + Math.max(wrongCount, 0));
        row.put("totalCount", (Integer) row.get("totalCount") + Math.max(totalCount, 0));
        if (!isBlank(suggestion)) {
            row.put("suggestion", suggestion.trim());
        }
    }

    private List<Map<String, Object>> buildLessonRecommendations(List<Map<String, Object>> weakPoints, List<Assignment> assignments) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int limit = Math.min(5, weakPoints.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> point = weakPoints.get(i);
            String name = String.valueOf(point.get("name"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", "围绕「" + name + "」做 8 分钟微讲解");
            row.put("focus", "先展示典型错误，再给 2 道同类变式练习，最后让学生用自己的话复述规则。");
            row.put("reason", "当前错误次数 " + point.get("wrongCount") + "，错误率约 " + point.get("wrongRate") + "%。");
            row.put("resourceHint", assignments.isEmpty()
                    ? "可从最近一次作业中抽取样例。"
                    : "可从《" + assignments.get(0).getTitle() + "》中抽取学生样例。");
            rows.add(row);
        }
        if (rows.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", "等待更多批改数据生成备课建议");
            row.put("focus", "完成一次作文或练习册批改后，系统会自动汇总知识点薄弱项。");
            row.put("reason", "暂无可统计的知识点错误。");
            row.put("resourceHint", "建议先演示学生端提交与教师端统计闭环。");
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Long> scoreDistribution(List<BigDecimal> scores, int totalScore) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            final double low = totalScore * i / 5.0;
            final double high = totalScore * (i + 1) / 5.0;
            final int idx = i;
            String label = (int) low + "-" + (idx == 4 ? totalScore : (int) high);
            long count = scores.stream()
                    .filter(s -> s.doubleValue() >= low && s.doubleValue() < high + (idx == 4 ? 0.01 : 0))
                    .count();
            distribution.put(label, count);
        }
        return distribution;
    }

    private BigDecimal effectiveScore(Essay essay) {
        if (essay.getFinalScore() != null) {
            return essay.getFinalScore();
        }
        if (essay.getAiScore() != null) {
            return essay.getAiScore();
        }
        return BigDecimal.ZERO;
    }

    private String studentName(Essay essay) {
        Student student = studentMapper.selectById(essay.getStudentId());
        if (student != null && !isBlank(student.getName())) {
            return student.getName();
        }
        return "学生ID: " + essay.getStudentId();
    }

    private boolean isGraded(Essay essay) {
        return "GRADED".equals(essay.getStatus()) || "TEACHER_REVIEWED".equals(essay.getStatus());
    }

    private boolean isProcessing(Essay essay) {
        return PROCESSING_STATUSES.contains(essay.getStatus());
    }

    private boolean needsReview(Essay essay) {
        return Boolean.TRUE.equals(essay.getReviewRequired()) && !"TEACHER_REVIEWED".equals(essay.getStatus());
    }

    private int totalScoreOf(Assignment assignment) {
        return assignment.getTotalScore() == null ? 100 : assignment.getTotalScore();
    }

    private String typeOf(Assignment assignment) {
        return isBlank(assignment.getAssignmentType()) ? "ESSAY" : assignment.getAssignmentType();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private double round(double value) {
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
