ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS assignment_type VARCHAR(20) DEFAULT 'ESSAY';
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS question_range VARCHAR(100);
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_images TEXT;
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_json TEXT;
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_mineru_markdown TEXT;
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_mineru_structure_json TEXT;
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_status VARCHAR(30) DEFAULT '';
ALTER TABLE tb_assignment ADD COLUMN IF NOT EXISTS answer_key_message VARCHAR(500) DEFAULT '';

ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS image_urls TEXT;
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS mineru_markdown TEXT;
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS mineru_structure_json TEXT;
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS workbook_result_json TEXT;

UPDATE tb_assignment SET assignment_type = 'ESSAY' WHERE assignment_type IS NULL OR assignment_type = '';
