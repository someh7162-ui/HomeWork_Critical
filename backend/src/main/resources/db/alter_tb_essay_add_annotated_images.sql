ALTER TABLE tb_essay
    ADD COLUMN annotated_image_url VARCHAR(500) COMMENT '带批注成品图路径',
    ADD COLUMN annotated_image_urls TEXT COMMENT '多页带批注成品图JSON数组';
