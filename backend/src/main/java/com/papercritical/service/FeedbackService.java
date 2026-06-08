package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.dto.FeedbackDTO;
import com.papercritical.entity.Student;
import com.papercritical.entity.Teacher;
import com.papercritical.entity.UserFeedback;
import com.papercritical.mapper.StudentMapper;
import com.papercritical.mapper.TeacherMapper;
import com.papercritical.mapper.UserFeedbackMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private final UserFeedbackMapper feedbackMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;

    public FeedbackService(UserFeedbackMapper feedbackMapper, TeacherMapper teacherMapper, StudentMapper studentMapper) {
        this.feedbackMapper = feedbackMapper;
        this.teacherMapper = teacherMapper;
        this.studentMapper = studentMapper;
    }

    public UserFeedback createTeacherFeedback(Long teacherId, FeedbackDTO dto) {
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new IllegalArgumentException("教师不存在");
        }
        UserFeedback feedback = buildFeedback(dto);
        feedback.setTeacherId(teacherId);
        feedback.setUserId(teacherId);
        feedback.setUserRole("TEACHER");
        feedback.setUserName(defaultText(teacher.getName(), teacher.getUsername()));
        feedbackMapper.insert(feedback);
        return feedback;
    }

    public UserFeedback createStudentFeedback(Long studentId, FeedbackDTO dto) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("学生不存在");
        }
        UserFeedback feedback = buildFeedback(dto);
        feedback.setTeacherId(student.getTeacherId());
        feedback.setUserId(studentId);
        feedback.setUserRole("STUDENT");
        feedback.setUserName(defaultText(student.getName(), student.getUsername()));
        feedback.setClassName(student.getClassName());
        feedbackMapper.insert(feedback);
        return feedback;
    }

    public List<Map<String, Object>> listForTeacher(Long teacherId) {
        return listByTeacherId(teacherId).stream().map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> listForStudent(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("学生不存在");
        }
        return listByTeacherId(student.getTeacherId()).stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> summaryForTeacher(Long teacherId) {
        return summarize(listByTeacherId(teacherId));
    }

    public Map<String, Object> summaryForStudent(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("学生不存在");
        }
        return summarize(listByTeacherId(student.getTeacherId()));
    }

    private UserFeedback buildFeedback(FeedbackDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("请填写反馈内容");
        }
        int rating = dto.getRating() == null ? 5 : dto.getRating();
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分需要在1到5之间");
        }
        String content = trim(dto.getContent());
        if (content.length() < 4) {
            throw new IllegalArgumentException("反馈内容至少填写4个字");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("反馈内容不能超过500字");
        }
        UserFeedback feedback = new UserFeedback();
        feedback.setRating(rating);
        feedback.setScene(limit(defaultText(dto.getScene(), "整体体验"), 40));
        feedback.setContent(content);
        feedback.setStatus("VISIBLE");
        feedback.setCreatedAt(LocalDateTime.now());
        return feedback;
    }

    private List<UserFeedback> listByTeacherId(Long teacherId) {
        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFeedback::getTeacherId, teacherId)
                .eq(UserFeedback::getStatus, "VISIBLE")
                .orderByDesc(UserFeedback::getCreatedAt)
                .last("LIMIT 100");
        return feedbackMapper.selectList(wrapper);
    }

    private Map<String, Object> summarize(List<UserFeedback> feedbacks) {
        Map<String, Object> result = new HashMap<>();
        result.put("count", feedbacks.size());
        if (feedbacks.isEmpty()) {
            result.put("averageRating", 0);
            result.put("sceneCounts", new LinkedHashMap<String, Long>());
            return result;
        }
        double avg = feedbacks.stream().mapToInt(f -> f.getRating() == null ? 0 : f.getRating()).average().orElse(0);
        result.put("averageRating", BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
        Map<String, Long> sceneCounts = feedbacks.stream()
                .collect(Collectors.groupingBy(f -> defaultText(f.getScene(), "整体体验"), LinkedHashMap::new, Collectors.counting()));
        result.put("sceneCounts", sceneCounts);
        return result;
    }

    private Map<String, Object> toMap(UserFeedback f) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", f.getId());
        map.put("userRole", f.getUserRole());
        map.put("userName", f.getUserName());
        map.put("className", f.getClassName());
        map.put("rating", f.getRating());
        map.put("scene", f.getScene());
        map.put("content", f.getContent());
        map.put("createdAt", f.getCreatedAt());
        return map;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        String text = trim(value);
        return text.isEmpty() ? fallback : text;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
