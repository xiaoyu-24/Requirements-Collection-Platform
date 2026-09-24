CREATE INDEX idx_requirements_submitted_deleted ON requirements(submitted_at, deleted);
CREATE INDEX idx_requirements_period_deleted ON requirements(period_start_date, period_end_date, deleted);
CREATE INDEX idx_requirements_type_deleted ON requirements(type, deleted);
CREATE INDEX idx_requirements_status_deleted ON requirements(status, deleted);
CREATE INDEX idx_requirements_save_type_deleted ON requirements(save_type, deleted);
CREATE INDEX idx_attachments_requirement_deleted ON attachments(requirement_id, deleted);
