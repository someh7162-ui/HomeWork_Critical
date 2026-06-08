ALTER TABLE tb_essay
    ADD COLUMN content_hash VARCHAR(64) NULL COMMENT '作文图片内容哈希，用于同图复用评分结果' AFTER student_id;

ALTER TABLE tb_essay
    ADD INDEX idx_assignment_student_hash (assignment_id, student_id, content_hash);
