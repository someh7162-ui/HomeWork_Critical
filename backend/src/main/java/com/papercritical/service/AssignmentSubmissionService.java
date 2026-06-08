package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.entity.Assignment;
import com.papercritical.entity.Essay;
import com.papercritical.entity.Student;
import com.papercritical.mapper.AssignmentMapper;
import com.papercritical.mapper.EssayMapper;
import com.papercritical.mapper.StudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AssignmentSubmissionService {

    private final AssignmentMapper assignmentMapper;
    private final StudentMapper studentMapper;
    private final EssayMapper essayMapper;
    private final EssayService essayService;

    public AssignmentSubmissionService(AssignmentMapper assignmentMapper, StudentMapper studentMapper,
                                       EssayMapper essayMapper, EssayService essayService) {
        this.assignmentMapper = assignmentMapper;
        this.studentMapper = studentMapper;
        this.essayMapper = essayMapper;
        this.essayService = essayService;
    }

    public List<Map<String, Object>> listStudentAssignments(Long studentId) {
        Student student = requireStudent(studentId);
        LambdaQueryWrapper<Assignment> wrapper = new LambdaQueryWrapper<>();
        if (isBlank(student.getClassName())) {
            wrapper.isNull(Assignment::getClassName).or().eq(Assignment::getClassName, "");
        } else {
            wrapper.eq(Assignment::getClassName, student.getClassName());
        }
        wrapper.orderByDesc(Assignment::getCreatedAt);
        List<Assignment> assignments = assignmentMapper.selectList(wrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Assignment assignment : assignments) {
            Map<String, Object> row = assignmentRow(assignment);
            Essay essay = latestEssay(assignment.getId(), student.getId());
            row.put("submission", essayRow(essay));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> studentAssignmentDetail(Long assignmentId, Long studentId) {
        Student student = requireStudent(studentId);
        Assignment assignment = requireVisibleAssignment(assignmentId, student);
        Map<String, Object> row = assignmentRow(assignment);
        row.put("submission", essayRow(latestEssay(assignmentId, studentId)));
        return row;
    }

    public Map<String, Object> submit(Long assignmentId, Long studentId, MultipartFile file) {
        Student student = requireStudent(studentId);
        requireVisibleAssignment(assignmentId, student);
        return essayService.uploadAndGrade(file, assignmentId, studentId, student.getTeacherId());
    }

    public Map<String, Object> submitWorkbook(Long assignmentId, Long studentId, MultipartFile[] files) {
        Student student = requireStudent(studentId);
        requireVisibleAssignment(assignmentId, student);
        return essayService.uploadAndGradeWorkbook(files, assignmentId, studentId, student.getTeacherId());
    }

    public Map<String, Object> studentEssayProgress(Long essayId, Long studentId) {
        Essay essay = requireStudentEssay(essayId, studentId);
        Student student = requireStudent(studentId);
        return essayService.getProgress(essay.getId(), student.getTeacherId());
    }

    public Essay studentEssayDetail(Long essayId, Long studentId) {
        Essay essay = requireStudentEssay(essayId, studentId);
        Student student = requireStudent(studentId);
        return essayService.getDetail(essay.getId(), student.getTeacherId());
    }

    public Map<String, Object> teacherSubmissionSummary(Long assignmentId, Long teacherId) {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !teacherId.equals(assignment.getTeacherId())) {
            throw new IllegalArgumentException("作业不存在或无权查看");
        }

        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getTeacherId, teacherId);
        if (!isBlank(assignment.getClassName())) {
            wrapper.eq(Student::getClassName, assignment.getClassName());
        }
        wrapper.orderByAsc(Student::getClassName, Student::getName);
        List<Student> students = studentMapper.selectList(wrapper);

        int submitted = 0;
        int graded = 0;
        int failed = 0;
        int reviewRequired = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Student student : students) {
            Essay essay = latestEssay(assignmentId, student.getId());
            if (essay != null) {
                submitted++;
                if ("GRADED".equals(essay.getStatus()) || "TEACHER_REVIEWED".equals(essay.getStatus())) {
                    graded++;
                }
                if ("FAILED".equals(essay.getStatus())) {
                    failed++;
                }
                if (Boolean.TRUE.equals(essay.getReviewRequired())
                        && !"TEACHER_REVIEWED".equals(essay.getStatus())) {
                    reviewRequired++;
                }
            }
            Map<String, Object> row = new HashMap<>();
            row.put("studentId", student.getId());
            row.put("studentName", student.getName());
            row.put("className", student.getClassName());
            row.put("studentNo", student.getStudentNo());
            row.put("submitted", essay != null);
            row.put("submission", essayRow(essay));
            rows.add(row);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("assignmentId", assignmentId);
        summary.put("className", assignment.getClassName());
        summary.put("totalStudents", students.size());
        summary.put("submittedCount", submitted);
        summary.put("unsubmittedCount", Math.max(students.size() - submitted, 0));
        summary.put("gradedCount", graded);
        summary.put("failedCount", failed);
        summary.put("reviewRequiredCount", reviewRequired);
        summary.put("students", rows);
        return summary;
    }

    public Map<String, Object> getStudentSummary(Long studentId) {
        Student student = requireStudent(studentId);
        List<Essay> essays = essayMapper.selectList(
                new LambdaQueryWrapper<Essay>()
                        .eq(Essay::getStudentId, studentId)
                        .in(Essay::getStatus, "GRADED", "TEACHER_REVIEWED")
                        .orderByDesc(Essay::getCreatedAt));

        Map<String, Object> summary = new HashMap<>();
        summary.put("studentName", student.getName());
        summary.put("className", student.getClassName());
        summary.put("totalEssays", essays.size());

        double avgScore = essays.stream().filter(e -> e.getAiScore() != null)
                .mapToDouble(e -> e.getAiScore().doubleValue()).average().orElse(0);
        summary.put("averageScore", Math.round(avgScore * 10.0) / 10.0);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Essay e : essays) {
            Map<String, Object> r = new HashMap<>();
            r.put("essayId", e.getId());
            r.put("score", e.getAiScore());
            r.put("createdAt", e.getCreatedAt());
            records.add(r);
        }
        summary.put("records", records);

        Set<String> kps = new HashSet<>();
        for (Essay e : essays) {
            if (e.getAiFeedback() != null) {
                try {
                    cn.hutool.json.JSONObject fb = new cn.hutool.json.JSONObject(e.getAiFeedback());
                    var kpArr = fb.getJSONArray("knowledgePoints");
                    if (kpArr != null) {
                        for (int i = 0; i < kpArr.size(); i++) {
                            kps.add(kpArr.getJSONObject(i).getStr("topic", ""));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        summary.put("knowledgePoints", new ArrayList<>(kps));
        return summary;
    }

    private Student requireStudent(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("学生不存在");
        }
        return student;
    }

    private Assignment requireVisibleAssignment(Long assignmentId, Student student) {
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new IllegalArgumentException("作业不存在或无权查看");
        }
        if (!isBlank(assignment.getClassName())
                && !assignment.getClassName().equals(student.getClassName())) {
            throw new IllegalArgumentException("该作业不属于你的班级");
        }
        return assignment;
    }

    private Essay requireStudentEssay(Long essayId, Long studentId) {
        Essay essay = essayMapper.selectById(essayId);
        if (essay == null || !studentId.equals(essay.getStudentId())) {
            throw new IllegalArgumentException("批改记录不存在或无权查看");
        }
        return essay;
    }

    private Essay latestEssay(Long assignmentId, Long studentId) {
        LambdaQueryWrapper<Essay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Essay::getAssignmentId, assignmentId)
                .eq(Essay::getStudentId, studentId)
                .orderByDesc(Essay::getCreatedAt)
                .last("LIMIT 1");
        return essayMapper.selectOne(wrapper);
    }

    private Map<String, Object> assignmentRow(Assignment assignment) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", assignment.getId());
        row.put("title", assignment.getTitle());
        row.put("assignmentType", assignment.getAssignmentType() == null ? "ESSAY" : assignment.getAssignmentType());
        row.put("question", assignment.getQuestion());
        row.put("modelEssay", assignment.getModelEssay());
        row.put("questionRange", assignment.getQuestionRange());
        row.put("answerKeyStatus", assignment.getAnswerKeyStatus());
        row.put("answerKeyMessage", assignment.getAnswerKeyMessage());
        row.put("wordLimitMin", assignment.getWordLimitMin());
        row.put("wordLimitMax", assignment.getWordLimitMax());
        row.put("totalScore", assignment.getTotalScore());
        row.put("className", assignment.getClassName());
        row.put("subject", assignment.getSubject());
        row.put("createdAt", assignment.getCreatedAt());
        return row;
    }

    private Map<String, Object> essayRow(Essay essay) {
        if (essay == null) {
            return null;
        }
        Map<String, Object> row = new HashMap<>();
        row.put("essayId", essay.getId());
        row.put("status", essay.getStatus());
        row.put("aiScore", essay.getAiScore());
        row.put("finalScore", essay.getFinalScore());
        row.put("confidenceScore", essay.getConfidenceScore());
        row.put("confidenceLevel", essay.getConfidenceLevel());
        row.put("reviewRequired", essay.getReviewRequired());
        row.put("teacherReviewNote", essay.getTeacherReviewNote());
        row.put("failureMessage", essay.getFailureMessage());
        row.put("createdAt", essay.getCreatedAt());
        return row;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
