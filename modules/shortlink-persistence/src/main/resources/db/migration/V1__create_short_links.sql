CREATE TABLE IF NOT EXISTS short_links (
    code VARCHAR(32) PRIMARY KEY,
    original_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_short_links_code_length CHECK (CHAR_LENGTH(code) BETWEEN 4 AND 32),
    CONSTRAINT chk_short_links_url_length CHECK (CHAR_LENGTH(original_url) BETWEEN 1 AND 2048),
    CONSTRAINT chk_short_links_expires_after_created CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_short_links_created_at_desc
    ON short_links (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_short_links_active_expires_at
    ON short_links (active, expires_at);
