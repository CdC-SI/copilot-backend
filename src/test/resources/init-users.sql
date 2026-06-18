INSERT INTO users (uuid, first_name, internal_user, last_name, status, username) VALUES
    ('12c81aae-3883-4840-8196-e8b18877ac59', 'Test', true, 'Test', 'ACTIVE', 'ZAS101340');

INSERT INTO user_entity_roles (user_entity_uuid, roles) VALUES
    ('12c81aae-3883-4840-8196-e8b18877ac59', 'USER'),
    ('12c81aae-3883-4840-8196-e8b18877ac59', 'ADMIN'),
    ('12c81aae-3883-4840-8196-e8b18877ac59', 'EXPERT');

INSERT INTO user_entity_organizations (user_entity_uuid, organizations) VALUES
    ('12c81aae-3883-4840-8196-e8b18877ac59', 'ZAS');