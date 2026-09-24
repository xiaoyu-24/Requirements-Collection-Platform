-- 保留历史负责人/协助人文本；仅在后续系统保存时写入新的账号关联。
ALTER TABLE system_collaborators ENGINE = InnoDB;
ALTER TABLE systems ENGINE = InnoDB;

ALTER TABLE systems
  ADD COLUMN owner_user_id BIGINT NULL,
  ADD INDEX idx_systems_owner_user (owner_user_id),
  ADD CONSTRAINT fk_systems_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id);

CREATE TABLE system_collaborator_users (
  system_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (system_id, user_id),
  CONSTRAINT fk_system_collaborator_users_system FOREIGN KEY (system_id) REFERENCES systems(id),
  CONSTRAINT fk_system_collaborator_users_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
