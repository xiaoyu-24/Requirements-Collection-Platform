ALTER TABLE system_versions
  ADD COLUMN description VARCHAR(2000) NULL,
  ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE system_versions ENGINE=InnoDB;

CREATE TABLE requirement_version_changes (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  requirement_id BIGINT NOT NULL,
  from_version_id BIGINT NULL,
  to_version_id BIGINT NULL,
  from_version_name VARCHAR(100) NULL,
  to_version_name VARCHAR(100) NULL,
  action VARCHAR(20) NOT NULL,
  operator_user_id BIGINT NOT NULL,
  operator_name VARCHAR(50) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_version_changes_requirement FOREIGN KEY (requirement_id) REFERENCES requirements(id),
  CONSTRAINT fk_version_changes_from_version FOREIGN KEY (from_version_id) REFERENCES system_versions(id),
  CONSTRAINT fk_version_changes_to_version FOREIGN KEY (to_version_id) REFERENCES system_versions(id),
  CONSTRAINT fk_version_changes_operator FOREIGN KEY (operator_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_version_changes_requirement_created
  ON requirement_version_changes (requirement_id, created_at, id);
