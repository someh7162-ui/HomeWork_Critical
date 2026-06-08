-- PostgreSQL: 为已有学生增加学生端登录账号。
-- 默认账号为 stu{id}，默认密码为 123456；应用启动后会自动迁移为 BCrypt。

ALTER TABLE tb_student
    ADD COLUMN IF NOT EXISTS username VARCHAR(50),
    ADD COLUMN IF NOT EXISTS password VARCHAR(100) DEFAULT '123456';

UPDATE tb_student
SET username = CONCAT('stu', id)
WHERE username IS NULL OR username = '';

UPDATE tb_student
SET password = '123456'
WHERE password IS NULL OR password = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_student_username ON tb_student (username);
