-- Seed Data for Simple Report Centre
-- Default admin password: admin123

-- 1. Default SUPER_ADMIN user
INSERT INTO USERS (ID, USERNAME, EMAIL, PASSWORD_HASH, ROLE, IS_ACTIVE, CREATED_AT, UPDATED_AT)
VALUES ('a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        'admin',
        'admin@reportcentre.io',
        '$2a$10$fUYeq93eghjE51p165A6..HwdDw6wQSa82ubGWP5fc2dso90zyeQ.',
        'SUPER_ADMIN',
        1,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 2. Sample OPERATOR user
INSERT INTO USERS (ID, USERNAME, EMAIL, PASSWORD_HASH, ROLE, IS_ACTIVE, CREATED_AT, UPDATED_AT)
VALUES ('b2c3d4e5-f6a7-8901-bcde-f12345678901',
        'operator',
        'operator@reportcentre.io',
        '$2a$10$fUYeq93eghjE51p165A6..HwdDw6wQSa82ubGWP5fc2dso90zyeQ.',
        'OPERATOR',
        1,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 3. Sample AUDITOR user
INSERT INTO USERS (ID, USERNAME, EMAIL, PASSWORD_HASH, ROLE, IS_ACTIVE, CREATED_AT, UPDATED_AT)
VALUES ('c3d4e5f6-a7b8-9012-cdef-123456789012',
        'auditor',
        'auditor@reportcentre.io',
        '$2a$10$fUYeq93eghjE51p165A6..HwdDw6wQSa82ubGWP5fc2dso90zyeQ.',
        'AUDITOR',
        1,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 4. Sample third-party client
INSERT INTO THIRD_PARTY_CLIENTS (ID, CLIENT_NAME, API_KEY, API_SECRET_HASH, STATUS, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
VALUES ('d4e5f6a7-b8c9-0123-defa-234567890123',
        'ACME Corp',
        'ak_live_sample_key_8f9021a8d0119e7a',
        '$2a$10$fUYeq93eghjE51p165A6..HwdDw6wQSa82ubGWP5fc2dso90zyeQ.',
        'ACTIVE',
        '',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
