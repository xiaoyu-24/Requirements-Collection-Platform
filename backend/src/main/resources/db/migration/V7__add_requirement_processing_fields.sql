ALTER TABLE requirements
    ADD COLUMN completed_at DATETIME(6) NULL,
    ADD COLUMN handled_by VARCHAR(50) NULL,
    ADD COLUMN completion_description TEXT NULL;
