CREATE TABLE urls (
    id BIGSERIAL PRIMARY KEY,
    original_url VARCHAR(2048) NOT NULL,
    short_code VARCHAR(10) NOT NULL UNIQUE,
    click_count BIGINT DEFAULT 0,
    user_id BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);
