CREATE TABLE systems (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  active_name_key VARCHAR(100) NULL UNIQUE,
  owner_name VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at DATETIME(6) NULL
);
CREATE TABLE system_collaborators (
  system_id BIGINT NOT NULL,
  collaborator_name VARCHAR(50) NOT NULL,
  CONSTRAINT fk_collaborators_system FOREIGN KEY (system_id) REFERENCES systems(id)
);
CREATE TABLE system_versions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  system_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at DATETIME(6) NULL,
  CONSTRAINT fk_versions_system FOREIGN KEY (system_id) REFERENCES systems(id)
);
CREATE TABLE requirements (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  requester_name VARCHAR(50) NULL,
  department VARCHAR(50) NULL,
  title VARCHAR(100) NULL,
  type VARCHAR(20) NULL,
  content TEXT NULL,
  system_id BIGINT NULL,
  target_version_id BIGINT NULL,
  period_start_date DATE NULL,
  period_end_date DATE NULL,
  status VARCHAR(30) NULL,
  save_type VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  submitted_at DATETIME(6) NULL,
  status_updated_at DATETIME(6) NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at DATETIME(6) NULL,
  CONSTRAINT fk_requirements_system FOREIGN KEY (system_id) REFERENCES systems(id),
  CONSTRAINT fk_requirements_version FOREIGN KEY (target_version_id) REFERENCES system_versions(id)
);
CREATE INDEX idx_requirements_system_deleted ON requirements(system_id, deleted);
CREATE INDEX idx_requirements_version_deleted ON requirements(target_version_id, deleted);
CREATE INDEX idx_requirements_filter ON requirements(department, requester_name, type, status, save_type, deleted);
