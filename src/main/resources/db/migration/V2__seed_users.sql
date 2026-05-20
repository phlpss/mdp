-- Seeds the 7 demo users as EntityData with type='User'.
-- Passwords are BCrypt hashes of "password".
INSERT INTO entity_data (id, type, payload)
VALUES ('00000000-0000-0000-0000-000000000001', 'User', '{
  "username": "barista1",
  "passwordHash": "$2a$12$xELgzHtRGOG4zDw1ZEJHa.PAGe3eq6oLtP5DlgjNyRUxKP58EEAhu",
  "roles": [
    "BARISTA"
  ],
  "storeLocationId": "00000000-0000-0000-0000-100000000001",
  "isActive": true
}'),
       ('00000000-0000-0000-0000-000000000002', 'User', '{
         "username": "shift_supervisor",
         "passwordHash": "$2a$12$xELgzHtRGOG4zDw1ZEJHa.PAGe3eq6oLtP5DlgjNyRUxKP58EEAhu",
         "roles": [
           "SHIFT_SUPERVISOR"
         ],
         "storeLocationId": "00000000-0000-0000-0000-100000000001",
         "isActive": true
       }'),
       ('00000000-0000-0000-0000-000000000003', 'User', '{
         "username": "store_manager",
         "passwordHash": "$2a$12$xELgzHtRGOG4zDw1ZEJHa.PAGe3eq6oLtP5DlgjNyRUxKP58EEAhu",
         "roles": [
           "STORE_MANAGER"
         ],
         "storeLocationId": "00000000-0000-0000-0000-100000000001",
         "isActive": true
       }'),
       ('00000000-0000-0000-0000-000000000004', 'User', '{
         "username": "hr_manager",
         "passwordHash": "$2a$12$xELgzHtRGOG4zDw1ZEJHa.PAGe3eq6oLtP5DlgjNyRUxKP58EEAhu",
         "roles": [
           "HR_MANAGER"
         ],
         "storeLocationId": "00000000-0000-0000-0000-100000000001",
         "isActive": true
       }'),
       ('00000000-0000-0000-0000-000000000005', 'User', '{
         "username": "accountant",
         "passwordHash": "$2a$12$xELgzHtRGOG4zDw1ZEJHa.PAGe3eq6oLtP5DlgjNyRUxKP58EEAhu",
         "roles": [
           "ACCOUNTANT"
         ],
         "storeLocationId": "00000000-0000-0000-0000-100000000001",
         "isActive": true
       }'),
       ('00000000-0000-0000-0000-000000000006', 'User', '{
         "username": "it_specialist",
         "passwordHash": "$2a$12$xELgzHtRGOG4zDw1ZEJHa.PAGe3eq6oLtP5DlgjNyRUxKP58EEAhu",
         "roles": [
           "IT_SPECIALIST"
         ],
         "storeLocationId": "00000000-0000-0000-0000-100000000001",
         "isActive": true
       }'),
       ('00000000-0000-0000-0000-000000000007', 'User', '{
         "username": "business_owner",
         "passwordHash": "$2a$12$xELgzHtRGOG4zDw1ZEJHa.PAGe3eq6oLtP5DlgjNyRUxKP58EEAhu",
         "roles": [
           "BUSINESS_OWNER"
         ],
         "storeLocationId": "00000000-0000-0000-0000-100000000001",
         "isActive": true
       }');