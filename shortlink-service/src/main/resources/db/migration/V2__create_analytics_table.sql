CREATE TABLE analytics (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(10) NOT NULL,
    click_timestamp TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    referrer VARCHAR(1024),
    user_agent VARCHAR(512),
    operating_system VARCHAR(50),
    browser VARCHAR(50)
);

CREATE INDEX idx_analytics_short_code ON analytics (short_code);
