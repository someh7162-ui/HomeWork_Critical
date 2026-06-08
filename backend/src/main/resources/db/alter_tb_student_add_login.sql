-- 为已有学生增加学生端登录账号。
-- 默认账号为 stu{id}，默认密码为 123456；应用启动后会自动迁移为 BCrypt。

ALTER TABLE tb_student
    ADD COLUMN username VARCHAR(50) DEFAULT NULL COMMENT '学生登录账号',
    ADD COLUMN password VARCHAR(100) DEFAULT '123456' COMMENT '学生登录密码';

UPDATE tb_student
SET username = CONCAT('stu', id)
WHERE username IS NULL OR username = '';

UPDATE tb_student
SET password = '123456'
WHERE password IS NULL OR password = '';

ALTER TABLE tb_student
    ADD UNIQUE INDEX idx_student_username (username);
