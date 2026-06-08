-- 支付与额度系统表

-- 教师额度表
CREATE TABLE IF NOT EXISTS tb_teacher_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    free_quota_total INT DEFAULT 50 COMMENT '免费额度总数',
    free_quota_used INT DEFAULT 0 COMMENT '已使用免费额度',
    paid_quota_total INT DEFAULT 0 COMMENT '付费额度总数',
    paid_quota_used INT DEFAULT 0 COMMENT '已使用付费额度',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/FROZEN',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师额度表';

-- 支付订单表
CREATE TABLE IF NOT EXISTS tb_payment_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    package_code VARCHAR(50) COMMENT '套餐编码',
    package_name VARCHAR(100) COMMENT '套餐名称',
    pay_channel VARCHAR(20) COMMENT '支付渠道: WECHAT/ALIPAY',
    payment_mode VARCHAR(30) COMMENT '支付模式: MOCK/WECHAT_MINI_PROGRAM',
    amount DECIMAL(10,2) COMMENT '支付金额',
    quota_amount INT COMMENT '购买额度数',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/CANCELLED/REFUNDED',
    transaction_id VARCHAR(100) COMMENT '第三方交易号',
    paid_at DATETIME COMMENT '支付完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_order_no (order_no),
    INDEX idx_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- 支付回调日志表
CREATE TABLE IF NOT EXISTS tb_payment_callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) COMMENT '订单号',
    pay_channel VARCHAR(20) COMMENT '支付渠道',
    callback_type VARCHAR(30) COMMENT '回调类型',
    payload TEXT COMMENT '回调原始数据',
    process_status VARCHAR(20) COMMENT '处理状态: SUCCESS/FAILED/IGNORED',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';

-- 额度使用日志表
CREATE TABLE IF NOT EXISTS tb_quota_usage_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    assignment_id BIGINT COMMENT '作业ID',
    student_id BIGINT COMMENT '学生ID',
    order_no VARCHAR(64) COMMENT '关联订单号',
    usage_type VARCHAR(30) COMMENT '类型: CONSUME/REFUND/GRANT/INIT',
    quota_source VARCHAR(20) COMMENT '额度来源: FREE/PAID',
    change_amount INT COMMENT '变动数量(正=增加,负=减少)',
    remaining_after INT COMMENT '操作后剩余总额度',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度使用日志表';
