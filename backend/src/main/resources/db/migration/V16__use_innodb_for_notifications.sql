ALTER TABLE notifications ENGINE = InnoDB;

ALTER TABLE notifications
  ADD CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id),
  ADD CONSTRAINT fk_notifications_requirement FOREIGN KEY (requirement_id) REFERENCES requirements(id);
