ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS annotated_image_url VARCHAR(500);
ALTER TABLE tb_essay ADD COLUMN IF NOT EXISTS annotated_image_urls TEXT;
