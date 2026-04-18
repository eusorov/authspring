CREATE TABLE password_reset_tokens (
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (email)
);
