ALTER TABLE attachments
    ADD COLUMN preview_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN preview_relative_path VARCHAR(500) NULL,
    ADD COLUMN preview_content_type VARCHAR(100) NULL,
    ADD COLUMN preview_generated_at DATETIME(6) NULL,
    ADD COLUMN preview_error_message VARCHAR(500) NULL,
    ADD COLUMN preview_retry_count INT NOT NULL DEFAULT 0;

UPDATE attachments
SET preview_status = 'DIRECT', preview_content_type = content_type
WHERE content_type = 'application/pdf' OR content_type LIKE 'image/%';
