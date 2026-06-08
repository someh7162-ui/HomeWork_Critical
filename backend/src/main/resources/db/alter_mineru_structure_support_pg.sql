-- MinerU全能解析结果持久化字段（PostgreSQL）
-- 用于保存原始Markdown和结构化JSON，便于回溯、二次分析和图片批注增强。

ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_mineru_markdown TEXT;
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_mineru_structure_json TEXT;

ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS mineru_markdown TEXT;
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS mineru_structure_json TEXT;
