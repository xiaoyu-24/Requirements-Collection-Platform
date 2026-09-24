ALTER TABLE requirements
    ADD COLUMN urgency VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    ADD INDEX idx_requirements_urgency_deleted (urgency, deleted);
