CREATE TABLE IF NOT EXISTS tb_feedback (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    user_name VARCHAR(80) NOT NULL,
    class_name VARCHAR(80) DEFAULT NULL,
    rating INTEGER NOT NULL DEFAULT 5,
    scene VARCHAR(40) NOT NULL DEFAULT '整体体验',
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feedback_teacher_created ON tb_feedback (teacher_id, created_at);
CREATE INDEX IF NOT EXISTS idx_feedback_role ON tb_feedback (user_role);
