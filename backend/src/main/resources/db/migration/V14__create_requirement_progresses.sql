CREATE TABLE requirement_progresses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requirement_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    author_id BIGINT NOT NULL,
    author_name VARCHAR(50) NOT NULL,
    status VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_requirement_progresses_requirement
        FOREIGN KEY (requirement_id) REFERENCES requirements(id),
    CONSTRAINT fk_requirement_progresses_author
        FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_requirement_progresses_requirement_created
    ON requirement_progresses (requirement_id, created_at, id);
