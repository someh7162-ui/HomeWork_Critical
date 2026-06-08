ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS confidence_score DECIMAL(5,4);
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS confidence_level VARCHAR(20) DEFAULT 'MEDIUM';
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS review_required BOOLEAN DEFAULT FALSE;
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS teacher_review_note VARCHAR(500) DEFAULT '';

UPDATE tb_essay
SET confidence_score = COALESCE(confidence_score, 0.7800),
    confidence_level = COALESCE(NULLIF(confidence_level, ''), 'MEDIUM'),
    review_required = COALESCE(review_required, FALSE)
WHERE status IN ('GRADED', 'TEACHER_REVIEWED');
