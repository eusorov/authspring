CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    email_verified_at TIMESTAMP NULL,
    date_closed DATE NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(8) NOT NULL,
    remember_token VARCHAR(100) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);
