-- =============================================================================
-- V3__seed_presentation_data.sql
-- Presentation & testing seed — MDP Coffee Shop
-- Period: 2026-04-22 → 2026-07-06  |  Fixed: 2026-06-03
-- =============================================================================

DELETE
FROM entity_data
WHERE type IN (
               'StoreLocation', 'Employee', 'User', 'Shift', 'LeaveRequest',
               'Transaction', 'Expense', 'InventoryItem'
    );

-- =============================================================================
-- 1. STORE LOCATIONS
-- =============================================================================
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'StoreLocation',
        '{
          "storeName": "MDP Coffee — Downtown",
          "address": "15 Rynok Square, Lviv 79008",
          "phone": "+380322610001",
          "isActive": true,
          "manager": "20000000-0000-0000-0000-000000000003"
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000002', 'StoreLocation',
        '{
          "storeName": "MDP Coffee — Shevchenko",
          "address": "44 Shevchenko Ave, Lviv 79005",
          "phone": "+380322610002",
          "isActive": true,
          "manager": "20000000-0000-0000-0000-000000000006"
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- =============================================================================
-- 2. EMPLOYEES  (ptoTotal/sickTotal/holidayTotal added; balances reflect leaves)
-- =============================================================================

-- 2.1  Business Owner (global)
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000001', 'Employee',
        '{
          "fullName": "Andriy Kovalenko",
          "email": "owner@mdpcoffee.com",
          "phone": "+380671110001",
          "role": "BUSINESS_OWNER",
          "salary": 5000,
          "hireDate": "2024-01-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 25,
          "ptoTotal": 25,
          "sickBalance": 15,
          "sickTotal": 15,
          "holidayBalance": 14,
          "holidayTotal": 14
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.2  IT Specialist (global)
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000002', 'Employee',
        '{
          "fullName": "Dmytro Bondarenko",
          "email": "it@mdpcoffee.com",
          "phone": "+380671110002",
          "role": "IT_SPECIALIST",
          "salary": 3500,
          "hireDate": "2024-03-15",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 20,
          "ptoTotal": 20,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 10,
          "holidayTotal": 10
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.3  Store Manager — Downtown
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000003', 'Employee',
        '{
          "fullName": "Olena Marchenko",
          "email": "olena.marchenko@mdpcoffee.com",
          "phone": "+380671110003",
          "role": "STORE_MANAGER",
          "salary": 2800,
          "hireDate": "2024-06-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 18,
          "ptoTotal": 20,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 10,
          "holidayTotal": 10
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.4  HR Manager — Downtown
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000004', 'Employee',
        '{
          "fullName": "Iryna Lysenko",
          "email": "iryna.lysenko@mdpcoffee.com",
          "phone": "+380671110004",
          "role": "HR_MANAGER",
          "salary": 2200,
          "hireDate": "2024-08-10",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 20,
          "ptoTotal": 20,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 10,
          "holidayTotal": 10
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.5  Accountant — Downtown
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000005', 'Employee',
        '{
          "fullName": "Vasyl Petrenko",
          "email": "vasyl.petrenko@mdpcoffee.com",
          "phone": "+380671110005",
          "role": "ACCOUNTANT",
          "salary": 2400,
          "hireDate": "2024-09-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 20,
          "ptoTotal": 20,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 10,
          "holidayTotal": 10
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.6  Store Manager — Shevchenko
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000006', 'Employee',
        '{
          "fullName": "Natalia Bondar",
          "email": "natalia.bondar@mdpcoffee.com",
          "phone": "+380671110006",
          "role": "STORE_MANAGER",
          "salary": 2800,
          "hireDate": "2024-07-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000002",
          "ptoBalance": 18,
          "ptoTotal": 20,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 10,
          "holidayTotal": 10
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.7  Barista 1 — Downtown  (ptoBalance=10 reflects 4 approved PTO days used)
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000007', 'Employee',
        '{
          "fullName": "Sofiia Kravchenko",
          "email": "sofiia.kravchenko@mdpcoffee.com",
          "phone": "+380671110007",
          "role": "BARISTA",
          "salary": 1400,
          "hireDate": "2025-01-15",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 10,
          "ptoTotal": 14,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 8,
          "holidayTotal": 8
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.8  Barista 2 — Downtown
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000008', 'Employee',
        '{
          "fullName": "Mykola Hrytsenko",
          "email": "mykola.hrytsenko@mdpcoffee.com",
          "phone": "+380671110008",
          "role": "BARISTA",
          "salary": 1400,
          "hireDate": "2025-03-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 14,
          "ptoTotal": 14,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 8,
          "holidayTotal": 8
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.9  Cashier — Downtown
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000009', 'Employee',
        '{
          "fullName": "Daryna Savchenko",
          "email": "daryna.savchenko@mdpcoffee.com",
          "phone": "+380671110009",
          "role": "CASHIER",
          "salary": 1200,
          "hireDate": "2025-05-10",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 12,
          "ptoTotal": 12,
          "sickBalance": 8,
          "sickTotal": 8,
          "holidayBalance": 8,
          "holidayTotal": 8
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.10  Supervisor — Downtown  (holidayBalance=7 reflects 3 approved HOLIDAY days used)
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000010', 'Employee',
        '{
          "fullName": "Taras Kovalchuk",
          "email": "taras.kovalchuk@mdpcoffee.com",
          "phone": "+380671110010",
          "role": "SHIFT_SUPERVISOR",
          "salary": 1800,
          "hireDate": "2024-11-20",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000001",
          "ptoBalance": 16,
          "ptoTotal": 16,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 7,
          "holidayTotal": 10
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.11  Supervisor — Shevchenko
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000011', 'Employee',
        '{
          "fullName": "Bohdan Rudenko",
          "email": "bohdan.rudenko@mdpcoffee.com",
          "phone": "+380671110011",
          "role": "SHIFT_SUPERVISOR",
          "salary": 1800,
          "hireDate": "2025-02-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000002",
          "ptoBalance": 16,
          "ptoTotal": 16,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 10,
          "holidayTotal": 10
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.12  Barista 1 — Shevchenko  (sickBalance=7 reflects 3 approved SICK days used)
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000012', 'Employee',
        '{
          "fullName": "Anastasiia Moroz",
          "email": "anastasiia.moroz@mdpcoffee.com",
          "phone": "+380671110012",
          "role": "BARISTA",
          "salary": 1400,
          "hireDate": "2025-04-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000002",
          "ptoBalance": 14,
          "ptoTotal": 14,
          "sickBalance": 7,
          "sickTotal": 10,
          "holidayBalance": 8,
          "holidayTotal": 8
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.13  Barista 2 — Shevchenko
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000013', 'Employee',
        '{
          "fullName": "Pavlo Shevchenko",
          "email": "pavlo.shevchenko@mdpcoffee.com",
          "phone": "+380671110013",
          "role": "BARISTA",
          "salary": 1400,
          "hireDate": "2025-06-15",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000002",
          "ptoBalance": 14,
          "ptoTotal": 14,
          "sickBalance": 10,
          "sickTotal": 10,
          "holidayBalance": 8,
          "holidayTotal": 8
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.14  Cashier 1 — Shevchenko
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000014', 'Employee',
        '{
          "fullName": "Yuliia Karpenko",
          "email": "yuliia.karpenko@mdpcoffee.com",
          "phone": "+380671110014",
          "role": "CASHIER",
          "salary": 1200,
          "hireDate": "2025-07-01",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000002",
          "ptoBalance": 12,
          "ptoTotal": 12,
          "sickBalance": 8,
          "sickTotal": 8,
          "holidayBalance": 8,
          "holidayTotal": 8
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- 2.15  Cashier 2 — Shevchenko
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000015', 'Employee',
        '{
          "fullName": "Oleksandr Tymchenko",
          "email": "oleksandr.tymchenko@mdpcoffee.com",
          "phone": "+380671110015",
          "role": "CASHIER",
          "salary": 1200,
          "hireDate": "2025-08-20",
          "isActive": true,
          "locationId": "10000000-0000-0000-0000-000000000002",
          "ptoBalance": 12,
          "ptoTotal": 12,
          "sickBalance": 8,
          "sickTotal": 8,
          "holidayBalance": 8,
          "holidayTotal": 8
        }',
        '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- =============================================================================
-- 3. USER ACCOUNTS  (password = 'password' for all)
-- =============================================================================
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('30000000-0000-0000-0000-000000000001', 'User', '{
  "username": "owner@mdpcoffee.com",
  "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
  "roles": [
    "BUSINESS_OWNER"
  ],
  "employeeId": "20000000-0000-0000-0000-000000000001",
  "isActive": true
}', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000002', 'User', '{
         "username": "it@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "IT_SPECIALIST"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000002",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000003', 'User', '{
         "username": "olena.marchenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "STORE_MANAGER"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000003",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000004', 'User', '{
         "username": "iryna.lysenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "HR_MANAGER"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000004",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000005', 'User', '{
         "username": "vasyl.petrenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "ACCOUNTANT"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000005",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000006', 'User', '{
         "username": "natalia.bondar@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "STORE_MANAGER"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000006",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000007', 'User', '{
         "username": "sofiia.kravchenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "BARISTA"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000007",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000008', 'User', '{
         "username": "mykola.hrytsenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "BARISTA"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000008",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000009', 'User', '{
         "username": "daryna.savchenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "CASHIER"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000009",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000010', 'User', '{
         "username": "taras.kovalchuk@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "SHIFT_SUPERVISOR"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000010",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000011', 'User', '{
         "username": "bohdan.rudenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "SHIFT_SUPERVISOR"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000011",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000012', 'User', '{
         "username": "anastasiia.moroz@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "BARISTA"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000012",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000013', 'User', '{
         "username": "pavlo.shevchenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "BARISTA"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000013",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000014', 'User', '{
         "username": "yuliia.karpenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "CASHIER"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000014",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00'),
       ('30000000-0000-0000-0000-000000000015', 'User', '{
         "username": "oleksandr.tymchenko@mdpcoffee.com",
         "passwordHash": "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi",
         "roles": [
           "CASHIER"
         ],
         "employeeId": "20000000-0000-0000-0000-000000000015",
         "isActive": true
       }', '2026-04-22 08:00:00', '2026-04-22 08:00:00');

-- =============================================================================
-- 4. INVENTORY ITEMS
-- =============================================================================
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('40000000-0000-0000-0000-000000000001', 'InventoryItem', '{
  "name": "Arabica Blend Beans",
  "sku": "BEAN-001",
  "category": "COFFEE_BEANS",
  "quantity": 45,
  "unit": "kg",
  "reorderLevel": 10,
  "unitCost": 18.50
}', '2026-04-22 09:00:00', '2026-05-20 09:00:00'),
       ('40000000-0000-0000-0000-000000000002', 'InventoryItem', '{
         "name": "Robusta Espresso Beans",
         "sku": "BEAN-002",
         "category": "COFFEE_BEANS",
         "quantity": 30,
         "unit": "kg",
         "reorderLevel": 8,
         "unitCost": 14.00
       }', '2026-04-22 09:00:00', '2026-05-18 09:00:00'),
       ('40000000-0000-0000-0000-000000000003', 'InventoryItem', '{
         "name": "Whole Milk 3.2%",
         "sku": "MILK-001",
         "category": "MILK",
         "quantity": 120,
         "unit": "l",
         "reorderLevel": 30,
         "unitCost": 0.85
       }', '2026-04-22 09:00:00', '2026-05-21 09:00:00'),
       ('40000000-0000-0000-0000-000000000004', 'InventoryItem', '{
         "name": "Oat Milk",
         "sku": "MILK-002",
         "category": "MILK",
         "quantity": 60,
         "unit": "l",
         "reorderLevel": 15,
         "unitCost": 1.40
       }', '2026-04-22 09:00:00', '2026-05-21 09:00:00'),
       ('40000000-0000-0000-0000-000000000005', 'InventoryItem', '{
         "name": "Vanilla Syrup",
         "sku": "SYR-001",
         "category": "SYRUPS",
         "quantity": 18,
         "unit": "l",
         "reorderLevel": 4,
         "unitCost": 7.20
       }', '2026-04-22 09:00:00', '2026-05-10 09:00:00'),
       ('40000000-0000-0000-0000-000000000006', 'InventoryItem', '{
         "name": "Caramel Syrup",
         "sku": "SYR-002",
         "category": "SYRUPS",
         "quantity": 15,
         "unit": "l",
         "reorderLevel": 4,
         "unitCost": 7.20
       }', '2026-04-22 09:00:00', '2026-05-10 09:00:00'),
       ('40000000-0000-0000-0000-000000000007', 'InventoryItem', '{
         "name": "Hazelnut Syrup",
         "sku": "SYR-003",
         "category": "SYRUPS",
         "quantity": 10,
         "unit": "l",
         "reorderLevel": 3,
         "unitCost": 7.50
       }', '2026-04-22 09:00:00', '2026-05-10 09:00:00'),
       ('40000000-0000-0000-0000-000000000008', 'InventoryItem', '{
         "name": "Paper Cups 12oz",
         "sku": "CUP-001",
         "category": "CUPS",
         "quantity": 2400,
         "unit": "pcs",
         "reorderLevel": 500,
         "unitCost": 0.06
       }', '2026-04-22 09:00:00', '2026-05-15 09:00:00'),
       ('40000000-0000-0000-0000-000000000009', 'InventoryItem', '{
         "name": "Paper Cups 16oz",
         "sku": "CUP-002",
         "category": "CUPS",
         "quantity": 1800,
         "unit": "pcs",
         "reorderLevel": 400,
         "unitCost": 0.08
       }', '2026-04-22 09:00:00', '2026-05-15 09:00:00'),
       ('40000000-0000-0000-0000-000000000010', 'InventoryItem', '{
         "name": "Plastic Lids",
         "sku": "CUP-003",
         "category": "CUPS",
         "quantity": 3500,
         "unit": "pcs",
         "reorderLevel": 600,
         "unitCost": 0.04
       }', '2026-04-22 09:00:00', '2026-05-15 09:00:00'),
       ('40000000-0000-0000-0000-000000000011', 'InventoryItem', '{
         "name": "Croissant (frozen)",
         "sku": "FOOD-001",
         "category": "FOOD",
         "quantity": 80,
         "unit": "pcs",
         "reorderLevel": 20,
         "unitCost": 0.95
       }', '2026-04-22 09:00:00', '2026-05-22 09:00:00'),
       ('40000000-0000-0000-0000-000000000012', 'InventoryItem', '{
         "name": "Cheesecake Slice",
         "sku": "FOOD-002",
         "category": "FOOD",
         "quantity": 24,
         "unit": "pcs",
         "reorderLevel": 6,
         "unitCost": 2.10
       }', '2026-04-22 09:00:00', '2026-05-22 09:00:00'),
       ('40000000-0000-0000-0000-000000000013', 'InventoryItem', '{
         "name": "All-Purpose Cleaner",
         "sku": "CLN-001",
         "category": "CLEANING",
         "quantity": 12,
         "unit": "l",
         "reorderLevel": 4,
         "unitCost": 3.50
       }', '2026-04-22 09:00:00', '2026-05-01 09:00:00'),
       ('40000000-0000-0000-0000-000000000014', 'InventoryItem', '{
         "name": "Disposable Gloves (box)",
         "sku": "CLN-002",
         "category": "CLEANING",
         "quantity": 8,
         "unit": "box",
         "reorderLevel": 2,
         "unitCost": 5.80
       }', '2026-04-22 09:00:00', '2026-05-01 09:00:00');

-- =============================================================================
-- 5. SHIFTS  (2026-04-22 – 2026-07-06)
--    startTime/endTime stored as ISO datetime strings: 'YYYY-MM-DDTHH:MM:SS'
--    FIX: employees 011-015 appear only once (duplicate block removed)
--    FIX: all operational staff extended to 2026-07-06
-- =============================================================================

-- ── DOWNTOWN ─────────────────────────────────────────────────────────────────

-- Sofiia Kravchenko (BARISTA) — daily morning 07:00–15:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000007',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T07:00:00',
               'endTime', shift_day::text || 'T15:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- Mykola Hrytsenko (BARISTA) — daily evening 14:00–22:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000008',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T14:00:00',
               'endTime', shift_day::text || 'T22:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- Daryna Savchenko (CASHIER) — daily morning 07:00–15:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000009',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T07:00:00',
               'endTime', shift_day::text || 'T15:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- Taras Kovalchuk (SUPERVISOR) — daily evening 14:00–22:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000010',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T14:00:00',
               'endTime', shift_day::text || 'T22:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- ── SHEVCHENKO ────────────────────────────────────────────────────────────────

-- Anastasiia Moroz (BARISTA) — daily morning 07:00–15:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000012',
               'storeLocationId', '10000000-0000-0000-0000-000000000002',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T07:00:00',
               'endTime', shift_day::text || 'T15:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- Pavlo Shevchenko (BARISTA) — daily evening 14:00–22:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000013',
               'storeLocationId', '10000000-0000-0000-0000-000000000002',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T14:00:00',
               'endTime', shift_day::text || 'T22:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- Yuliia Karpenko (CASHIER) — daily morning 07:00–15:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000014',
               'storeLocationId', '10000000-0000-0000-0000-000000000002',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T07:00:00',
               'endTime', shift_day::text || 'T15:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- Oleksandr Tymchenko (CASHIER) — daily evening 14:00–22:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000015',
               'storeLocationId', '10000000-0000-0000-0000-000000000002',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T14:00:00',
               'endTime', shift_day::text || 'T22:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- Bohdan Rudenko (SUPERVISOR) — daily evening 14:00–22:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000011',
               'storeLocationId', '10000000-0000-0000-0000-000000000002',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T14:00:00',
               'endTime', shift_day::text || 'T22:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '06:00:00'::time,
       shift_day + '06:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day;

-- ── OFFICE STAFF (Mon–Fri / Mon–Sat) ──────────────────────────────────────────

-- Andriy Kovalenko (OWNER) — Mon–Fri 09:00–17:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000001',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T09:00:00',
               'endTime', shift_day::text || 'T17:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '08:00:00'::time,
       shift_day + '08:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day
WHERE EXTRACT(DOW FROM shift_day) BETWEEN 1 AND 5;

-- Dmytro Bondarenko (IT) — Mon–Fri 09:00–17:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000002',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T09:00:00',
               'endTime', shift_day::text || 'T17:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '08:00:00'::time,
       shift_day + '08:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day
WHERE EXTRACT(DOW FROM shift_day) BETWEEN 1 AND 5;

-- Olena Marchenko (MANAGER Downtown) — Mon–Sat 08:00–16:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000003',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T08:00:00',
               'endTime', shift_day::text || 'T16:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '07:00:00'::time,
       shift_day + '07:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day
WHERE EXTRACT(DOW FROM shift_day) BETWEEN 1 AND 6;

-- Iryna Lysenko (HR) — Mon–Fri 09:00–17:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000004',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T09:00:00',
               'endTime', shift_day::text || 'T17:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '08:00:00'::time,
       shift_day + '08:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day
WHERE EXTRACT(DOW FROM shift_day) BETWEEN 1 AND 5;

-- Vasyl Petrenko (ACCOUNTANT) — Mon–Fri 09:00–17:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000005',
               'storeLocationId', '10000000-0000-0000-0000-000000000001',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T09:00:00',
               'endTime', shift_day::text || 'T17:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '08:00:00'::time,
       shift_day + '08:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day
WHERE EXTRACT(DOW FROM shift_day) BETWEEN 1 AND 5;

-- Natalia Bondar (MANAGER Shevchenko) — Mon–Sat 08:00–16:00
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Shift',
       jsonb_build_object(
               'employeeId', '20000000-0000-0000-0000-000000000006',
               'storeLocationId', '10000000-0000-0000-0000-000000000002',
               'shiftDate', shift_day::text,
               'startTime', shift_day::text || 'T08:00:00',
               'endTime', shift_day::text || 'T16:00:00',
               'shiftStatus', CASE WHEN shift_day < CURRENT_DATE THEN 'COMPLETED' ELSE 'SCHEDULED' END
       ),
       shift_day + '07:00:00'::time,
       shift_day + '07:00:00'::time
FROM generate_series('2026-04-22'::date, '2026-07-06'::date, '1 day'::interval) AS shift_day
WHERE EXTRACT(DOW FROM shift_day) BETWEEN 1 AND 6;

-- =============================================================================
-- 6. LEAVE REQUESTS  (daysRequested added; reason→notes; stale dates fixed)
-- =============================================================================
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES

-- APPROVED past PTO — Sofiia Kravchenko, 4 working days (Tue Apr 28 – Fri May 1)
('50000000-0000-0000-0000-000000000001', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000007",
   "leaveType": "PTO",
   "startDate": "2026-04-28",
   "endDate": "2026-05-02",
   "notes": "Family vacation",
   "leaveStatus": "APPROVED",
   "daysRequested": 4
 }',
 '2026-04-20 10:00:00', '2026-04-21 09:00:00'),

-- APPROVED past SICK — Anastasiia Moroz, 3 working days (Tue May 5 – Thu May 7)
('50000000-0000-0000-0000-000000000002', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000012",
   "leaveType": "SICK",
   "startDate": "2026-05-05",
   "endDate": "2026-05-07",
   "notes": "Flu",
   "leaveStatus": "APPROVED",
   "daysRequested": 3
 }',
 '2026-05-05 07:30:00', '2026-05-05 09:00:00'),

-- REJECTED PTO — Daryna Savchenko, 4 working days (Mon May 11 – Thu May 14)
('50000000-0000-0000-0000-000000000003', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000009",
   "leaveType": "PTO",
   "startDate": "2026-05-10",
   "endDate": "2026-05-14",
   "notes": "Personal trip",
   "leaveStatus": "REJECTED",
   "daysRequested": 4
 }',
 '2026-05-03 11:00:00', '2026-05-04 10:00:00'),

-- PENDING PTO — Mykola Hrytsenko, 4 working days (Tue Jun 9 – Fri Jun 12)
-- FIX: was 2026-06-02–06-06 (start date already past as of 2026-06-03)
('50000000-0000-0000-0000-000000000004', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000008",
   "leaveType": "PTO",
   "startDate": "2026-06-09",
   "endDate": "2026-06-13",
   "notes": "Holiday",
   "leaveStatus": "PENDING",
   "daysRequested": 4
 }',
 '2026-05-20 14:00:00', '2026-05-20 14:00:00'),

-- PENDING SICK — Pavlo Shevchenko, 2 working days (Tue Jun 16 – Wed Jun 17)
-- FIX: was 2026-05-23–05-24 (Saturday–Sunday = 0 working days)
('50000000-0000-0000-0000-000000000005', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000013",
   "leaveType": "SICK",
   "startDate": "2026-06-16",
   "endDate": "2026-06-17",
   "notes": "Doctor appointment",
   "leaveStatus": "PENDING",
   "daysRequested": 2
 }',
 '2026-05-22 08:00:00', '2026-05-22 08:00:00'),

-- APPROVED upcoming HOLIDAY — Taras Kovalchuk, 3 working days (Wed Jun 10 – Fri Jun 12)
('50000000-0000-0000-0000-000000000006', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000010",
   "leaveType": "HOLIDAY",
   "startDate": "2026-06-10",
   "endDate": "2026-06-14",
   "notes": "Summer holiday",
   "leaveStatus": "APPROVED",
   "daysRequested": 3
 }',
 '2026-05-15 09:00:00', '2026-05-16 10:00:00'),

-- PENDING_CANCELLATION PTO — Yuliia Karpenko, 4 working days (Tue Jun 16 – Fri Jun 19)
('50000000-0000-0000-0000-000000000007', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000014",
   "leaveType": "PTO",
   "startDate": "2026-06-16",
   "endDate": "2026-06-20",
   "notes": "Vacation",
   "leaveStatus": "PENDING_CANCELLATION",
   "daysRequested": 4
 }',
 '2026-05-10 10:00:00', '2026-05-21 11:00:00'),

-- CANCELLED PTO — Oleksandr Tymchenko, 4 working days (Tue May 26 – Fri May 29)
('50000000-0000-0000-0000-000000000008', 'LeaveRequest',
 '{
   "employeeId": "20000000-0000-0000-0000-000000000015",
   "leaveType": "PTO",
   "startDate": "2026-05-26",
   "endDate": "2026-05-29",
   "notes": "Trip cancelled",
   "leaveStatus": "CANCELLED",
   "daysRequested": 4
 }',
 '2026-05-08 15:00:00', '2026-05-09 09:00:00');

-- =============================================================================
-- 7. TRANSACTIONS  (2026-04-22 – 2026-05-22, ~15-20/day per store)
-- =============================================================================
INSERT INTO entity_data (type, payload, created_at, updated_at)
SELECT 'Transaction',
       jsonb_build_object(
               'receiptNumber', 'RCP-' || to_char(tx_time, 'YYYYMMDD') || '-' ||
                                lpad(row_number() OVER (PARTITION BY tx_time::date ORDER BY tx_time)::text, 4, '0'),
               'amount', (floor(random() * 280 + 45) / 10.0),
               'paymentMethod', (ARRAY ['CASH','CARD','MOBILE'])[floor(random() * 3 + 1)::int],
               'cashierId', cashier_id,
               'locationId', loc_id,
               'createdAt', tx_time
       ),
       tx_time,
       tx_time
FROM (SELECT tx_time,
             CASE
                 WHEN loc_flag = 1
                     THEN '20000000-0000-0000-0000-000000000009'
                 ELSE (ARRAY ['20000000-0000-0000-0000-000000000014','20000000-0000-0000-0000-000000000015'])[floor(random() * 2 + 1)::int]
                 END AS cashier_id,
             CASE
                 WHEN loc_flag = 1
                     THEN '10000000-0000-0000-0000-000000000001'
                 ELSE '10000000-0000-0000-0000-000000000002'
                 END AS loc_id
      FROM (SELECT ('2026-04-22'::date + (n || ' hours')::interval
          + (floor(random() * 60) || ' minutes')::interval) AS tx_time,
                   (CASE WHEN n % 2 = 0 THEN 1 ELSE 2 END)  AS loc_flag
            FROM generate_series(0, (30 * 24) - 1) AS n
                     CROSS JOIN generate_series(1, 18) AS _t
            WHERE random() < 0.75) raw
      WHERE EXTRACT(HOUR FROM tx_time) BETWEEN 7 AND 21) final;

-- =============================================================================
-- 8. EXPENSES
-- =============================================================================
INSERT INTO entity_data (id, type, payload, created_at, updated_at)
VALUES ('60000000-0000-0000-0000-000000000001', 'Expense', '{
  "description": "Arabica beans restock — 20kg",
  "category": "FOOD_COST",
  "amount": 370.00,
  "date": "2026-04-25",
  "approvedBy": "20000000-0000-0000-0000-000000000003"
}', '2026-04-25 10:00:00', '2026-04-25 10:00:00'),
       ('60000000-0000-0000-0000-000000000002', 'Expense', '{
         "description": "Electricity bill — Downtown April",
         "category": "UTILITIES",
         "amount": 480.00,
         "date": "2026-04-30",
         "approvedBy": "20000000-0000-0000-0000-000000000005"
       }', '2026-04-30 12:00:00', '2026-04-30 12:00:00'),
       ('60000000-0000-0000-0000-000000000003', 'Expense', '{
         "description": "Electricity bill — Shevchenko April",
         "category": "UTILITIES",
         "amount": 420.00,
         "date": "2026-04-30",
         "approvedBy": "20000000-0000-0000-0000-000000000005"
       }', '2026-04-30 12:00:00', '2026-04-30 12:00:00'),
       ('60000000-0000-0000-0000-000000000004', 'Expense', '{
         "description": "Coffee cups & lids restock",
         "category": "SUPPLIES",
         "amount": 215.00,
         "date": "2026-05-03",
         "approvedBy": "20000000-0000-0000-0000-000000000003"
       }', '2026-05-03 09:00:00', '2026-05-03 09:00:00'),
       ('60000000-0000-0000-0000-000000000005', 'Expense', '{
         "description": "Milk delivery — both stores May week 1",
         "category": "FOOD_COST",
         "amount": 290.00,
         "date": "2026-05-05",
         "approvedBy": "20000000-0000-0000-0000-000000000006"
       }', '2026-05-05 08:00:00', '2026-05-05 08:00:00'),
       ('60000000-0000-0000-0000-000000000006', 'Expense', '{
         "description": "Espresso machine servicing — Downtown",
         "category": "MAINTENANCE",
         "amount": 350.00,
         "date": "2026-05-08",
         "approvedBy": "20000000-0000-0000-0000-000000000001"
       }', '2026-05-08 14:00:00', '2026-05-08 14:00:00'),
       ('60000000-0000-0000-0000-000000000007', 'Expense', '{
         "description": "Social media ads — May campaign",
         "category": "MARKETING",
         "amount": 200.00,
         "date": "2026-05-10",
         "approvedBy": "20000000-0000-0000-0000-000000000001"
       }', '2026-05-10 11:00:00', '2026-05-10 11:00:00'),
       ('60000000-0000-0000-0000-000000000008', 'Expense', '{
         "description": "Cleaning supplies restock",
         "category": "SUPPLIES",
         "amount": 95.00,
         "date": "2026-05-12",
         "approvedBy": "20000000-0000-0000-0000-000000000003"
       }', '2026-05-12 10:00:00', '2026-05-12 10:00:00'),
       ('60000000-0000-0000-0000-000000000009', 'Expense', '{
         "description": "Syrups restock — both stores",
         "category": "FOOD_COST",
         "amount": 310.00,
         "date": "2026-05-14",
         "approvedBy": "20000000-0000-0000-0000-000000000006"
       }', '2026-05-14 09:00:00', '2026-05-14 09:00:00'),
       ('60000000-0000-0000-0000-000000000010', 'Expense', '{
         "description": "Internet & POS subscription — May",
         "category": "UTILITIES",
         "amount": 120.00,
         "date": "2026-05-15",
         "approvedBy": "20000000-0000-0000-0000-000000000002"
       }', '2026-05-15 10:00:00', '2026-05-15 10:00:00'),
       ('60000000-0000-0000-0000-000000000011', 'Expense', '{
         "description": "Food items restock — pastries",
         "category": "FOOD_COST",
         "amount": 180.00,
         "date": "2026-05-18",
         "approvedBy": "20000000-0000-0000-0000-000000000003"
       }', '2026-05-18 08:30:00', '2026-05-18 08:30:00'),
       ('60000000-0000-0000-0000-000000000012', 'Expense', '{
         "description": "Electricity bill — Downtown May",
         "category": "UTILITIES",
         "amount": 465.00,
         "date": "2026-05-20",
         "approvedBy": "20000000-0000-0000-0000-000000000005"
       }', '2026-05-20 12:00:00', '2026-05-20 12:00:00'),
       ('60000000-0000-0000-0000-000000000013', 'Expense', '{
         "description": "Electricity bill — Shevchenko May",
         "category": "UTILITIES",
         "amount": 405.00,
         "date": "2026-05-20",
         "approvedBy": "20000000-0000-0000-0000-000000000005"
       }', '2026-05-20 12:00:00', '2026-05-20 12:00:00'),
       ('60000000-0000-0000-0000-000000000014', 'Expense', '{
         "description": "Robusta beans restock — 15kg",
         "category": "FOOD_COST",
         "amount": 210.00,
         "date": "2026-05-21",
         "approvedBy": "20000000-0000-0000-0000-000000000006"
       }', '2026-05-21 09:00:00', '2026-05-21 09:00:00');