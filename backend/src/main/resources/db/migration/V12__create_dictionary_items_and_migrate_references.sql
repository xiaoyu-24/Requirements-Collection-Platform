CREATE TABLE dictionary_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    name VARCHAR(50) NOT NULL,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    record_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_dictionary_items_category_name UNIQUE (category, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_dictionary_items_category_disabled_name
    ON dictionary_items (category, disabled, name);

INSERT INTO dictionary_items (category, name, disabled, record_version, created_at, updated_at)
VALUES
    ('DEPARTMENT', 'IT部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', 'FAE部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '总经办', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '供应链部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '财务部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '产品部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '市场运营部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '业务部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '品质部', FALSE, 0, NOW(6), NOW(6)),
    ('DEPARTMENT', '人力资源部', FALSE, 0, NOW(6), NOW(6)),
    ('REQUIREMENT_TYPE', 'BUG', FALSE, 0, NOW(6), NOW(6)),
    ('REQUIREMENT_TYPE', '需求', FALSE, 0, NOW(6), NOW(6));

-- Preserve unexpected historical values as disabled entries instead of dropping data.
INSERT IGNORE INTO dictionary_items (category, name, disabled, record_version, created_at, updated_at)
SELECT 'DEPARTMENT', TRIM(department), TRUE, 0, NOW(6), NOW(6)
FROM requirements
WHERE department IS NOT NULL AND TRIM(department) <> '';

INSERT IGNORE INTO dictionary_items (category, name, disabled, record_version, created_at, updated_at)
SELECT 'DEPARTMENT', TRIM(department), TRUE, 0, NOW(6), NOW(6)
FROM users
WHERE department IS NOT NULL AND TRIM(department) <> '';

INSERT IGNORE INTO dictionary_items (category, name, disabled, record_version, created_at, updated_at)
SELECT 'REQUIREMENT_TYPE',
       CASE
           WHEN UPPER(TRIM(type)) = 'BUG' THEN 'BUG'
           WHEN UPPER(TRIM(type)) = 'REQUIREMENT' THEN '需求'
           ELSE TRIM(type)
       END,
       CASE
           WHEN UPPER(TRIM(type)) IN ('BUG', 'REQUIREMENT') THEN FALSE
           ELSE TRUE
       END,
       0,
       NOW(6),
       NOW(6)
FROM requirements
WHERE type IS NOT NULL AND TRIM(type) <> '';

ALTER TABLE requirements
    ADD COLUMN department_id BIGINT NULL,
    ADD COLUMN type_id BIGINT NULL;

ALTER TABLE users
    ADD COLUMN department_id BIGINT NULL;

UPDATE requirements requirement
JOIN dictionary_items department
    ON department.category = 'DEPARTMENT'
   AND department.name = CONVERT(TRIM(requirement.department) USING utf8mb4) COLLATE utf8mb4_unicode_ci
SET requirement.department_id = department.id
WHERE requirement.department IS NOT NULL AND TRIM(requirement.department) <> '';

UPDATE requirements requirement
JOIN dictionary_items type_item
    ON type_item.category = 'REQUIREMENT_TYPE'
   AND type_item.name = CONVERT(
       CASE
           WHEN UPPER(TRIM(requirement.type)) = 'BUG' THEN 'BUG'
           WHEN UPPER(TRIM(requirement.type)) = 'REQUIREMENT' THEN '需求'
           ELSE TRIM(requirement.type)
       END USING utf8mb4
   ) COLLATE utf8mb4_unicode_ci
SET requirement.type_id = type_item.id
WHERE requirement.type IS NOT NULL AND TRIM(requirement.type) <> '';

UPDATE users user_item
JOIN dictionary_items department
    ON department.category = 'DEPARTMENT'
   AND department.name = CONVERT(TRIM(user_item.department) USING utf8mb4) COLLATE utf8mb4_unicode_ci
SET user_item.department_id = department.id
WHERE user_item.department IS NOT NULL AND TRIM(user_item.department) <> '';

ALTER TABLE requirements
    ADD CONSTRAINT fk_requirements_department FOREIGN KEY (department_id) REFERENCES dictionary_items(id),
    ADD CONSTRAINT fk_requirements_type FOREIGN KEY (type_id) REFERENCES dictionary_items(id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES dictionary_items(id);

DROP INDEX idx_requirements_filter ON requirements;
DROP INDEX idx_requirements_type_deleted ON requirements;

ALTER TABLE requirements
    DROP COLUMN department,
    DROP COLUMN type;

ALTER TABLE users
    DROP COLUMN department;

CREATE INDEX idx_requirements_filter
    ON requirements (department_id, requester_name, type_id, status, save_type, deleted);
CREATE INDEX idx_requirements_type_deleted
    ON requirements (type_id, deleted);
CREATE INDEX idx_requirements_department_deleted
    ON requirements (department_id, deleted);
CREATE INDEX idx_users_department
    ON users (department_id);
