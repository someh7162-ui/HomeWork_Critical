package com.papercritical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.papercritical.entity.Student;
import com.papercritical.mapper.StudentMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {

    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentMapper studentMapper, PasswordEncoder passwordEncoder) {
        this.studentMapper = studentMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Student add(Student student, Long teacherId) {
        student.setTeacherId(teacherId);
        prepareLoginFields(student);
        studentMapper.insert(student);
        ensureGeneratedUsername(student);
        return student;
    }

    public List<Student> list(Long teacherId, String className) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getTeacherId, teacherId);
        if (className != null && !className.trim().isEmpty()) {
            wrapper.eq(Student::getClassName, className.trim());
        }
        wrapper.orderByAsc(Student::getClassName, Student::getName);
        return studentMapper.selectList(wrapper);
    }

    public List<String> listClasses(Long teacherId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Student::getClassName)
                .eq(Student::getTeacherId, teacherId)
                .isNotNull(Student::getClassName)
                .ne(Student::getClassName, "")
                .groupBy(Student::getClassName)
                .orderByAsc(Student::getClassName);
        return studentMapper.selectList(wrapper).stream()
                .map(Student::getClassName)
                .collect(java.util.stream.Collectors.toList());
    }

    public int batchAdd(List<Student> students, Long teacherId) {
        int count = 0;
        for (Student s : students) {
            s.setTeacherId(teacherId);
            prepareLoginFields(s);
            LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Student::getTeacherId, teacherId)
                    .eq(Student::getClassName, s.getClassName())
                    .eq(Student::getName, s.getName());
            if (studentMapper.selectCount(wrapper) > 0) {
                continue;
            }
            studentMapper.insert(s);
            ensureGeneratedUsername(s);
            count++;
        }
        return count;
    }

    public boolean delete(Long id, Long teacherId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getId, id).eq(Student::getTeacherId, teacherId);
        return studentMapper.delete(wrapper) > 0;
    }

    public int deleteByClass(String className, Long teacherId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getClassName, className).eq(Student::getTeacherId, teacherId);
        return studentMapper.delete(wrapper);
    }

    private void prepareLoginFields(Student student) {
        if (isBlank(student.getPassword())) {
            student.setPassword(passwordEncoder.encode("123456"));
        } else if (!student.getPassword().startsWith("$2a$")) {
            student.setPassword(passwordEncoder.encode(student.getPassword()));
        }
        if (!isBlank(student.getUsername())) {
            student.setUsername(student.getUsername().trim());
        } else {
            student.setUsername(null);
        }
    }

    private void ensureGeneratedUsername(Student student) {
        if (!isBlank(student.getUsername())) {
            return;
        }
        Student update = new Student();
        update.setId(student.getId());
        update.setUsername("stu" + student.getId());
        studentMapper.updateById(update);
        student.setUsername(update.getUsername());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
