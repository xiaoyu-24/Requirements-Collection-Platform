ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NULL;

UPDATE users
SET role = CASE WHEN admin_role = TRUE THEN 'ADMIN' ELSE 'HANDLER' END;

ALTER TABLE users
    MODIFY COLUMN role VARCHAR(20) NOT NULL;

ALTER TABLE users
    DROP COLUMN admin_role;

CREATE INDEX idx_users_role ON users (role);
