ALTER TABLE system_versions
    ADD COLUMN active_name_key VARCHAR(100) NULL;

UPDATE system_versions
SET active_name_key = LOWER(TRIM(name))
WHERE deleted = FALSE;

CREATE UNIQUE INDEX uk_system_versions_active_name
    ON system_versions(system_id, active_name_key);
