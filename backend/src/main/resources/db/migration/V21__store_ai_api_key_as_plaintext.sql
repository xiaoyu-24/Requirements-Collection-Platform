ALTER TABLE ai_config
    CHANGE COLUMN api_key_encrypted api_key VARCHAR(1000) NULL;

UPDATE ai_config SET api_key = NULL;
