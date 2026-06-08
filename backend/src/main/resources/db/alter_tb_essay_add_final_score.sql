ALTER TABLE tb_essay
    ADD COLUMN final_score DECIMAL(5,2) NULL COMMENT '教师最终确认分数' AFTER ai_score;
