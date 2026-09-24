SET @xqfx_current_version = (
    SELECT version
    FROM flyway_schema_history
    WHERE success = TRUE
    ORDER BY installed_rank DESC
    LIMIT 1
);

SET @xqfx_sql = IF(
    @xqfx_current_version = '12'
        AND EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'requirements'
              AND CONSTRAINT_NAME = 'fk_requirements_department'
        ),
    'ALTER TABLE requirements DROP FOREIGN KEY fk_requirements_department',
    'DO 0'
);
PREPARE xqfx_statement FROM @xqfx_sql;
EXECUTE xqfx_statement;
DEALLOCATE PREPARE xqfx_statement;

SET @xqfx_sql = IF(
    @xqfx_current_version = '12'
        AND EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'requirements'
              AND CONSTRAINT_NAME = 'fk_requirements_type'
        ),
    'ALTER TABLE requirements DROP FOREIGN KEY fk_requirements_type',
    'DO 0'
);
PREPARE xqfx_statement FROM @xqfx_sql;
EXECUTE xqfx_statement;
DEALLOCATE PREPARE xqfx_statement;

SET @xqfx_sql = IF(
    @xqfx_current_version = '15'
        AND EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'notifications'
              AND CONSTRAINT_NAME = 'fk_notifications_recipient'
        ),
    'ALTER TABLE notifications DROP FOREIGN KEY fk_notifications_recipient',
    'DO 0'
);
PREPARE xqfx_statement FROM @xqfx_sql;
EXECUTE xqfx_statement;
DEALLOCATE PREPARE xqfx_statement;

SET @xqfx_sql = IF(
    @xqfx_current_version = '15'
        AND EXISTS (
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'notifications'
              AND CONSTRAINT_NAME = 'fk_notifications_requirement'
        ),
    'ALTER TABLE notifications DROP FOREIGN KEY fk_notifications_requirement',
    'DO 0'
);
PREPARE xqfx_statement FROM @xqfx_sql;
EXECUTE xqfx_statement;
DEALLOCATE PREPARE xqfx_statement;
