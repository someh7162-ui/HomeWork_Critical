ALTER TABLE tb_essay
    ADD COLUMN ai_raw_response MEDIUMTEXT COMMENT 'AI原始响应' AFTER ai_feedback,
    MODIFY COLUMN status VARCHAR(30) DEFAULT 'UPLOADED' COMMENT '批改状态: UPLOADED/OCR_PROCESSING/OCR_DONE/AI_PROCESSING/GRADED/TEACHER_REVIEWED/FAILED',
    ADD COLUMN failure_reason VARCHAR(50) COMMENT '失败原因编码' AFTER status,
    ADD COLUMN failure_message VARCHAR(500) COMMENT '失败说明' AFTER failure_reason,
    ADD COLUMN grading_started_at DATETIME COMMENT '批改开始时间' AFTER failure_message,
    ADD COLUMN grading_completed_at DATETIME COMMENT '批改完成时间' AFTER grading_started_at;
