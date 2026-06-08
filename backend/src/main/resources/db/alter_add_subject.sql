-- 智阅教育 学科扩展迁移脚本
-- 为 tb_assignment 增加 subject 字段，插入全科/语文/数学测试数据

USE paper_critical;

-- 1. tb_assignment 增加 subject 列
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS subject VARCHAR(50) DEFAULT '英语' COMMENT '科目' AFTER class_name;

-- 2. 插入全科教师测试账号（一个用户可以批改语数英三门）
INSERT IGNORE INTO tb_teacher (username, password, name, subject) VALUES
('teacher0', '123456', '陈老师', '全科');

-- 3. 为全科教师创建测试学生
-- 全科教师的 teacher_id 取决于插入顺序（在新库中为 4）
INSERT IGNORE INTO tb_student (teacher_id, name, class_name, student_no)
SELECT id, '周明', '高一(1)班', '2024000' FROM tb_teacher WHERE username = 'teacher0'
UNION ALL
SELECT id, '吴桐', '高一(3)班', '2024000' FROM tb_teacher WHERE username = 'teacher0'
UNION ALL
SELECT id, '郑宇', '高一(5)班', '2024000' FROM tb_teacher WHERE username = 'teacher0';

-- 4. 插入语文教师测试账号
INSERT IGNORE INTO tb_teacher (username, password, name, subject) VALUES
('teacher2', '123456', '李老师', '语文');

-- 5. 插入数学教师测试账号
INSERT IGNORE INTO tb_teacher (username, password, name, subject) VALUES
('teacher3', '123456', '王老师', '数学');

-- 6. 为语文教师创建测试学生（teacher_id=2）
INSERT IGNORE INTO tb_student (teacher_id, name, class_name, student_no) VALUES
(2, '张伟', '高一(3)班', '2024006'),
(2, '李娜', '高一(3)班', '2024007'),
(2, '王磊', '高一(4)班', '2024008');

-- 7. 为数学教师创建测试学生（teacher_id=3）
INSERT IGNORE INTO tb_student (teacher_id, name, class_name, student_no) VALUES
(3, '赵敏', '高一(5)班', '2024009'),
(3, '陈鹏', '高一(5)班', '2024010'),
(3, '刘婷', '高一(6)班', '2024011');
