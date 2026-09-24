CREATE TABLE ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    service_url VARCHAR(500),
    model_name VARCHAR(200),
    api_key_encrypted VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO ai_config (id, enabled, service_url, model_name, api_key_encrypted, created_at, updated_at)
VALUES (1, FALSE, NULL, NULL, NULL, NOW(6), NOW(6));
