CREATE TABLE password_resets (
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL,
    CONSTRAINT pk_password_resets PRIMARY KEY (email, token)
);

CREATE INDEX idx_password_resets_email ON password_resets (email);
