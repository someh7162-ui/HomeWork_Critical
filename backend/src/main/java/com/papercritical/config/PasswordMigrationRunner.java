package com.papercritical.config;

import com.papercritical.entity.Student;
import com.papercritical.entity.Teacher;
import com.papercritical.mapper.StudentMapper;
import com.papercritical.mapper.TeacherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PasswordMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordMigrationRunner.class);

    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;

    public PasswordMigrationRunner(TeacherMapper teacherMapper, StudentMapper studentMapper,
                                   PasswordEncoder passwordEncoder) {
        this.teacherMapper = teacherMapper;
        this.studentMapper = studentMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        List<Teacher> teachers = teacherMapper.selectList(null);
        int migrated = 0;
        for (Teacher t : teachers) {
            String pw = t.getPassword();
            if (pw == null || pw.isEmpty()) continue;
            if (pw.startsWith("$2a$")) continue;
            String hashed = passwordEncoder.encode(pw);
            Teacher update = new Teacher();
            update.setId(t.getId());
            update.setPassword(hashed);
            teacherMapper.updateById(update);
            migrated++;
            log.info("Migrated password for teacher: {}", t.getUsername());
        }
        if (migrated > 0) {
            log.info("Password migration complete: {} accounts migrated to BCrypt", migrated);
        }
        migrateStudentPasswords();
    }

    private void migrateStudentPasswords() {
        List<Student> students = studentMapper.selectList(null);
        int migrated = 0;
        int generatedUsernames = 0;
        for (Student s : students) {
            boolean needUpdate = false;
            Student update = new Student();
            update.setId(s.getId());

            if (s.getUsername() == null || s.getUsername().trim().isEmpty()) {
                update.setUsername("stu" + s.getId());
                needUpdate = true;
                generatedUsernames++;
            }

            String pw = s.getPassword();
            if (pw == null || pw.isEmpty()) {
                update.setPassword(passwordEncoder.encode("123456"));
                needUpdate = true;
                migrated++;
            } else if (!pw.startsWith("$2a$")) {
                update.setPassword(passwordEncoder.encode(pw));
                needUpdate = true;
                migrated++;
            }

            if (needUpdate) {
                studentMapper.updateById(update);
            }
        }
        if (generatedUsernames > 0) {
            log.info("Student account migration complete: {} usernames generated", generatedUsernames);
        }
        if (migrated > 0) {
            log.info("Student password migration complete: {} accounts migrated to BCrypt", migrated);
        }
    }
}
