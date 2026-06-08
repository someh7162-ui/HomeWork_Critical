-- 智阅教育 PostgreSQL 初始化脚本

CREATE TABLE IF NOT EXISTS tb_teacher (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    name VARCHAR(100),
    openid VARCHAR(200),
    subject VARCHAR(50) DEFAULT '英语',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_teacher_quota (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    free_remaining INT DEFAULT 0,
    paid_remaining INT DEFAULT 0,
    free_used INT DEFAULT 0,
    paid_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_assignment (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    assignment_type VARCHAR(20) DEFAULT 'ESSAY',
    question TEXT,
    model_essay TEXT,
    question_range VARCHAR(100),
    answer_key_images TEXT,
    answer_key_json TEXT,
    answer_key_mineru_markdown TEXT,
    answer_key_mineru_structure_json TEXT,
    answer_key_status VARCHAR(30) DEFAULT '',
    answer_key_message VARCHAR(500) DEFAULT '',
    word_limit_min INT DEFAULT 0,
    word_limit_max INT DEFAULT 0,
    total_score INT DEFAULT 15,
    class_name VARCHAR(100),
    subject VARCHAR(50) DEFAULT '英语',
    scoring_criteria TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_student (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    class_name VARCHAR(100),
    student_no VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_essay (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    student_name VARCHAR(100),
    student_no VARCHAR(100),
    image_url VARCHAR(500),
    image_urls TEXT,
    annotated_image_url VARCHAR(500),
    annotated_image_urls TEXT,
    content_hash VARCHAR(64),
    image_md5 VARCHAR(64),
    ocr_text TEXT,
    mineru_markdown TEXT,
    mineru_structure_json TEXT,
    ai_score NUMERIC(6,1),
    ai_feedback TEXT,
    ai_raw_response TEXT,
    workbook_result_json TEXT,
    final_score NUMERIC(6,1),
    status VARCHAR(50) DEFAULT 'UPLOADED',
    failure_reason VARCHAR(200) DEFAULT '',
    failure_message TEXT DEFAULT '',
    assignment_total_score INT,
    grading_started_at TIMESTAMP,
    grading_completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_feedback (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    user_name VARCHAR(80) NOT NULL,
    class_name VARCHAR(80) DEFAULT NULL,
    rating INTEGER NOT NULL DEFAULT 5,
    scene VARCHAR(40) NOT NULL DEFAULT '整体体验',
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feedback_teacher_created ON tb_feedback (teacher_id, created_at);
CREATE INDEX IF NOT EXISTS idx_feedback_role ON tb_feedback (user_role);

CREATE TABLE IF NOT EXISTS tb_payment_order (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    order_no VARCHAR(100),
    package_code VARCHAR(100),
    package_name VARCHAR(200),
    quota_amount INT DEFAULT 0,
    amount_cents INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'PENDING',
    pay_method VARCHAR(50) DEFAULT 'WECHAT',
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_payment_callback_log (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(100),
    raw_body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_quota_usage_log (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    quota_type VARCHAR(50),
    assignment_id BIGINT,
    student_id BIGINT,
    essay_id BIGINT,
    remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 种子数据：英语教师
INSERT INTO tb_teacher (username, password, name, subject) VALUES
('teacher1', '123456', '张老师', '英语'),
('teacher2', '123456', '李老师', '语文'),
('teacher3', '123456', '王老师', '数学'),
('teacher0', '123456', '陈老师', '全科')
ON CONFLICT (username) DO NOTHING;

-- 额度初始化
INSERT INTO tb_teacher_quota (teacher_id, free_remaining)
SELECT id, 100 FROM tb_teacher
WHERE id NOT IN (SELECT teacher_id FROM tb_teacher_quota);

-- 英语教师学生
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 1, '李明', '高一(1)班', '2024001' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=1 AND student_no='2024001');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 1, '王芳', '高一(1)班', '2024002' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=1 AND student_no='2024002');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 1, '赵强', '高一(1)班', '2024003' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=1 AND student_no='2024003');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 1, '刘洋', '高一(2)班', '2024004' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=1 AND student_no='2024004');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 1, '陈静', '高一(2)班', '2024005' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=1 AND student_no='2024005');

-- 语文教师学生
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 2, '张伟', '高一(3)班', '2024006' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=2 AND student_no='2024006');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 2, '李娜', '高一(3)班', '2024007' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=2 AND student_no='2024007');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 2, '王磊', '高一(4)班', '2024008' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=2 AND student_no='2024008');

-- 数学教师学生
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 3, '赵敏', '高一(5)班', '2024009' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=3 AND student_no='2024009');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 3, '陈鹏', '高一(5)班', '2024010' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=3 AND student_no='2024010');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 3, '刘婷', '高一(6)班', '2024011' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=3 AND student_no='2024011');

-- 全科教师学生
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 4, '周明', '高一(1)班', '2024000' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=4 AND student_no='2024000');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 4, '吴桐', '高一(3)班', '2024000' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=4 AND student_no='2024000');
INSERT INTO tb_student (teacher_id, name, class_name, student_no)
SELECT 4, '郑宇', '高一(5)班', '2024000' WHERE NOT EXISTS (SELECT 1 FROM tb_student WHERE teacher_id=4 AND student_no='2024000');
