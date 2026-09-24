ALTER TABLE requirements
  ADD COLUMN requester_user_id BIGINT NULL,
  ADD COLUMN assignee_user_id BIGINT NULL,
  ADD INDEX idx_requirements_requester_user (requester_user_id),
  ADD INDEX idx_requirements_assignee_user (assignee_user_id),
  ADD CONSTRAINT fk_requirements_requester_user FOREIGN KEY (requester_user_id) REFERENCES users(id),
  ADD CONSTRAINT fk_requirements_assignee_user FOREIGN KEY (assignee_user_id) REFERENCES users(id);

UPDATE requirements requirement_item
JOIN users user_mapping
  ON CONVERT(user_mapping.display_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   = CONVERT(requirement_item.requester_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
LEFT JOIN users duplicate_mapping
  ON CONVERT(duplicate_mapping.display_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
   = CONVERT(requirement_item.requester_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
 AND duplicate_mapping.id <> user_mapping.id
SET requirement_item.requester_user_id = user_mapping.id
WHERE requirement_item.requester_user_id IS NULL
  AND duplicate_mapping.id IS NULL;

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  recipient_id BIGINT NOT NULL,
  requirement_id BIGINT NULL,
  notification_type VARCHAR(40) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content VARCHAR(500) NOT NULL,
  event_key VARCHAR(120) NOT NULL,
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id),
  CONSTRAINT fk_notifications_requirement FOREIGN KEY (requirement_id) REFERENCES requirements(id)
);

CREATE UNIQUE INDEX uk_notifications_recipient_event ON notifications (recipient_id, event_key);
CREATE INDEX idx_notifications_recipient_read_created ON notifications (recipient_id, read_at, created_at);
