CREATE TABLE attachments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  requirement_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  relative_path VARCHAR(500) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at DATETIME(6) NULL,
  CONSTRAINT fk_attachments_requirement FOREIGN KEY (requirement_id) REFERENCES requirements(id)
);
