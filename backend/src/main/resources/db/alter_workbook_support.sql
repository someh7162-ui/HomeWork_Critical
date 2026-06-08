ALTER TABLE tb_assignment
    ADD COLUMN assignment_type VARCHAR(20) DEFAULT 'ESSAY' COMMENT '作业类型: ESSAY/WORKBOOK',
    ADD COLUMN question_range VARCHAR(100) COMMENT '练习册题号范围',
    ADD COLUMN answer_key_images TEXT COMMENT '答案页图片JSON数组',
    ADD COLUMN answer_key_json MEDIUMTEXT COMMENT '结构化答案JSON',
    ADD COLUMN answer_key_status VARCHAR(30) DEFAULT '' COMMENT '答案解析状态: PENDING/PARSING/READY/FAILED',
    ADD COLUMN answer_key_message VARCHAR(500) DEFAULT '' COMMENT '答案解析说明';

ALTER TABLE tb_essay
    ADD COLUMN image_urls TEXT COMMENT '多页提交图片JSON数组',
    ADD COLUMN workbook_result_json MEDIUMTEXT COMMENT '练习册逐题批改结果JSON';

UPDATE tb_assignment SET assignment_type = 'ESSAY' WHERE assignment_type IS NULL OR assignment_type = '';
