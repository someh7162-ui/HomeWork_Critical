ALTER TABLE tb_essay
    ADD COLUMN confidence_score DECIMAL(5,4) COMMENT 'AI批改置信度，范围0-1',
    ADD COLUMN confidence_level VARCHAR(20) DEFAULT 'MEDIUM' COMMENT '置信度等级: HIGH/MEDIUM/LOW',
    ADD COLUMN review_required TINYINT(1) DEFAULT 0 COMMENT '是否需要教师重点复核',
    ADD COLUMN teacher_review_note VARCHAR(500) DEFAULT '' COMMENT '教师复核说明';

UPDATE tb_essay
SET confidence_score = COALESCE(confidence_score, 0.7800),
    confidence_level = COALESCE(NULLIF(confidence_level, ''), 'MEDIUM'),
    review_required = COALESCE(review_required, 0)
WHERE status IN ('GRADED', 'TEACHER_REVIEWED');
