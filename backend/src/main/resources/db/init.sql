-- 智阅教育 数据库初始化脚本（支持语/数/英三门主科）

CREATE DATABASE IF NOT EXISTS paper_critical
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE paper_critical;

-- 教师表
CREATE TABLE IF NOT EXISTS tb_teacher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码(明文，生产环境应加密)',
    openid VARCHAR(64) DEFAULT NULL COMMENT '微信openid',
    name VARCHAR(50) NOT NULL COMMENT '教师姓名',
    subject VARCHAR(50) DEFAULT '英语' COMMENT '科目',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX idx_t_wechat (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师表';

-- 作业表
CREATE TABLE IF NOT EXISTS tb_assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    title VARCHAR(200) NOT NULL COMMENT '作业标题',
    assignment_type VARCHAR(20) DEFAULT 'ESSAY' COMMENT '作业类型: ESSAY/WORKBOOK',
    question TEXT NOT NULL COMMENT '题目要求',
    model_essay TEXT NOT NULL COMMENT '范文/参考答案',
    question_range VARCHAR(100) COMMENT '练习册题号范围',
    answer_key_images TEXT COMMENT '答案页图片JSON数组',
    answer_key_json MEDIUMTEXT COMMENT '结构化答案JSON',
    answer_key_mineru_markdown MEDIUMTEXT COMMENT 'MinerU答案页Markdown解析结果',
    answer_key_mineru_structure_json MEDIUMTEXT COMMENT 'MinerU答案页结构化解析原始JSON',
    answer_key_status VARCHAR(30) DEFAULT '' COMMENT '答案解析状态',
    answer_key_message VARCHAR(500) DEFAULT '' COMMENT '答案解析说明',
    word_limit_min INT DEFAULT 0 COMMENT '最小字数',
    word_limit_max INT DEFAULT 0 COMMENT '最大字数',
    total_score INT DEFAULT 15 COMMENT '满分',
    class_name VARCHAR(100) COMMENT '班级',
    subject VARCHAR(50) DEFAULT '英语' COMMENT '科目',
    scoring_criteria TEXT COMMENT '评分标准JSON',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业表';

-- 学生表
CREATE TABLE IF NOT EXISTS tb_student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '所属教师ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '学生登录账号',
    password VARCHAR(100) DEFAULT '123456' COMMENT '学生登录密码',
    name VARCHAR(50) NOT NULL COMMENT '学生姓名',
    class_name VARCHAR(100) COMMENT '班级',
    student_no VARCHAR(50) COMMENT '学号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX idx_student_username (username),
    INDEX idx_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生表';

-- 作文批改记录表
CREATE TABLE IF NOT EXISTS tb_essay (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL COMMENT '作业ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    content_hash VARCHAR(64) COMMENT '作文图片内容哈希，用于同图复用评分结果',
    image_url VARCHAR(500) COMMENT '原始图片路径',
    image_urls TEXT COMMENT '多页提交图片JSON数组',
    annotated_image_url VARCHAR(500) COMMENT '带批注成品图路径',
    annotated_image_urls TEXT COMMENT '多页带批注成品图JSON数组',
    ocr_text TEXT COMMENT 'OCR识别文本',
    mineru_markdown MEDIUMTEXT COMMENT 'MinerU Markdown解析结果',
    mineru_structure_json MEDIUMTEXT COMMENT 'MinerU结构化解析原始JSON',
    ai_score DECIMAL(5,2) COMMENT 'AI评分',
    final_score DECIMAL(5,2) COMMENT '教师最终确认分数',
    confidence_score DECIMAL(5,4) COMMENT 'AI批改置信度，范围0-1',
    confidence_level VARCHAR(20) DEFAULT 'MEDIUM' COMMENT '置信度等级: HIGH/MEDIUM/LOW',
    review_required TINYINT(1) DEFAULT 0 COMMENT '是否需要教师重点复核',
    teacher_review_note VARCHAR(500) DEFAULT '' COMMENT '教师复核说明',
    ai_feedback TEXT COMMENT 'AI评分依据JSON',
    ai_raw_response MEDIUMTEXT COMMENT 'AI原始响应',
    workbook_result_json MEDIUMTEXT COMMENT '练习册逐题批改结果JSON',
    status VARCHAR(30) DEFAULT 'UPLOADED' COMMENT '批改状态: UPLOADED/OCR_PROCESSING/OCR_DONE/AI_PROCESSING/GRADED/TEACHER_REVIEWED/FAILED',
    failure_reason VARCHAR(50) COMMENT '失败原因编码',
    failure_message VARCHAR(500) COMMENT '失败说明',
    grading_started_at DATETIME COMMENT '批改开始时间',
    grading_completed_at DATETIME COMMENT '批改完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_student_id (student_id),
    INDEX idx_assignment_student_hash (assignment_id, student_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作文批改记录表';

-- 教师额度表
CREATE TABLE IF NOT EXISTS tb_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    user_name VARCHAR(80) NOT NULL,
    class_name VARCHAR(80) DEFAULT NULL,
    rating INT NOT NULL DEFAULT 5,
    scene VARCHAR(40) NOT NULL DEFAULT '整体体验',
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feedback_teacher_created (teacher_id, created_at),
    INDEX idx_feedback_role (user_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户体验反馈表';

CREATE TABLE IF NOT EXISTS tb_teacher_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    free_quota_total INT DEFAULT 50 COMMENT '免费额度总数',
    free_quota_used INT DEFAULT 0 COMMENT '已使用免费额度',
    paid_quota_total INT DEFAULT 0 COMMENT '付费额度总数',
    paid_quota_used INT DEFAULT 0 COMMENT '已使用付费额度',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/FROZEN',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_tq_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师额度表';

-- 支付订单表
CREATE TABLE IF NOT EXISTS tb_payment_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    package_code VARCHAR(50) COMMENT '套餐编码',
    package_name VARCHAR(100) COMMENT '套餐名称',
    pay_channel VARCHAR(20) COMMENT '支付渠道: WECHAT/ALIPAY',
    payment_mode VARCHAR(30) COMMENT '支付模式: MOCK/WECHAT_MINI_PROGRAM',
    amount DECIMAL(10,2) COMMENT '支付金额',
    quota_amount INT COMMENT '购买额度数',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/CANCELLED/REFUNDED',
    transaction_id VARCHAR(100) COMMENT '第三方交易号',
    paid_at DATETIME COMMENT '支付完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_po_order_no (order_no),
    INDEX idx_po_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- 支付回调日志表
CREATE TABLE IF NOT EXISTS tb_payment_callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) COMMENT '订单号',
    pay_channel VARCHAR(20) COMMENT '支付渠道',
    callback_type VARCHAR(30) COMMENT '回调类型',
    payload TEXT COMMENT '回调原始数据',
    process_status VARCHAR(20) COMMENT '处理状态: SUCCESS/FAILED/IGNORED',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pcl_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';

-- 额度使用日志表
CREATE TABLE IF NOT EXISTS tb_quota_usage_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    assignment_id BIGINT COMMENT '作业ID',
    student_id BIGINT COMMENT '学生ID',
    order_no VARCHAR(64) COMMENT '关联订单号',
    usage_type VARCHAR(30) COMMENT '类型: CONSUME/REFUND/GRANT/INIT',
    quota_source VARCHAR(20) COMMENT '额度来源: FREE/PAID',
    change_amount INT COMMENT '变动数量(正=增加,负=减少)',
    remaining_after INT COMMENT '操作后剩余总额度',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_qul_teacher_id (teacher_id),
    INDEX idx_qul_assignment_id (assignment_id),
    INDEX idx_qul_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度使用日志表';

-- 插入测试教师数据
INSERT INTO tb_teacher (username, password, name, subject) VALUES
('teacher1', '123456', '张老师', '英语'),
('teacher2', '123456', '李老师', '语文'),
('teacher3', '123456', '王老师', '数学'),
('teacher0', '123456', '陈老师', '全科');

-- 全科教师学生（teacher_id=4，跨三个班级）
INSERT INTO tb_student (teacher_id, username, password, name, class_name, student_no) VALUES
(4, 'stu4001', '123456', '周明', '高一(1)班', '2024000'),
(4, 'stu4002', '123456', '吴桐', '高一(3)班', '2024000'),
(4, 'stu4003', '123456', '郑宇', '高一(5)班', '2024000');

-- 插入测试学生数据（teacher_id=1 对应 英语 teacher1）
INSERT INTO tb_student (teacher_id, username, password, name, class_name, student_no) VALUES
(1, 'stu1001', '123456', '李明', '高一(1)班', '2024001'),
(1, 'stu1002', '123456', '王芳', '高一(1)班', '2024002'),
(1, 'stu1003', '123456', '赵强', '高一(1)班', '2024003'),
(1, 'stu1004', '123456', '刘洋', '高一(2)班', '2024004'),
(1, 'stu1005', '123456', '陈静', '高一(2)班', '2024005');

-- 语文教师学生（teacher_id=2）
INSERT INTO tb_student (teacher_id, username, password, name, class_name, student_no) VALUES
(2, 'stu2001', '123456', '张伟', '高一(3)班', '2024006'),
(2, 'stu2002', '123456', '李娜', '高一(3)班', '2024007'),
(2, 'stu2003', '123456', '王磊', '高一(4)班', '2024008');

-- 数学教师学生（teacher_id=3）
INSERT INTO tb_student (teacher_id, username, password, name, class_name, student_no) VALUES
(3, 'stu3001', '123456', '赵敏', '高一(5)班', '2024009'),
(3, 'stu3002', '123456', '陈鹏', '高一(5)班', '2024010'),
(3, 'stu3003', '123456', '刘婷', '高一(6)班', '2024011');
