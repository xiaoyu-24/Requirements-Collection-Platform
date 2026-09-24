ALTER TABLE systems
    ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE requirements
    ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;
