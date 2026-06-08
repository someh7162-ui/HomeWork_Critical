CREATE TABLE IF NOT EXISTS tb_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    user_name VARCHAR(80) NOT NULL,
    class_name VARCHAR(80) DEFAULT NULL,
    rating INT NOT NULL DEFAULT 5,
    scene VARCHAR(40) NOT NULL DEFAULT '整体体验',
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feedback_teacher_created (teacher_id, created_at),
    INDEX idx_feedback_role (user_role)
);
