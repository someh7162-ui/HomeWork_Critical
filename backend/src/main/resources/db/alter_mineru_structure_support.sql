-- MinerU全能解析结果持久化字段（MySQL）
-- 用于保存原始Markdown和结构化JSON，便于回溯、二次分析和图片批注增强。

SET @schema_name := DATABASE();

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_assignment ADD COLUMN answer_key_mineru_markdown MEDIUMTEXT COMMENT ''MinerU答案页Markdown解析结果''',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_assignment'
      AND COLUMN_NAME = 'answer_key_mineru_markdown'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_assignment ADD COLUMN answer_key_mineru_structure_json MEDIUMTEXT COMMENT ''MinerU答案页结构化解析原始JSON''',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_assignment'
      AND COLUMN_NAME = 'answer_key_mineru_structure_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_essay ADD COLUMN mineru_markdown MEDIUMTEXT COMMENT ''MinerU Markdown解析结果''',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_essay'
      AND COLUMN_NAME = 'mineru_markdown'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_essay ADD COLUMN mineru_structure_json MEDIUMTEXT COMMENT ''MinerU结构化解析原始JSON''',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_essay'
      AND COLUMN_NAME = 'mineru_structure_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
