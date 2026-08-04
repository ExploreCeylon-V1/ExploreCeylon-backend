-- ─────────────────────────────────────────────────────────────
-- Demo & System Initial Seed Data
-- Note: Hardcoded entity seeds for destinations, hidden_gems, events,
-- tour_guides, and vehicles have been cleared per system configuration.
-- Users (Admin / Demo accounts), Booking status migrations, and Location
-- gazetteer lookups for geocoding are preserved.
-- ─────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────
-- 1. Essential System Users
-- ─────────────────────────────────────────────────────────────
INSERT INTO users (name, email, password, role, auth_provider, nationality, created_at, updated_at)
SELECT * FROM (VALUES
    ('Admin User',
     'admin@exploreceylon.com',
     '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
     'ADMIN',
     'LOCAL',
     'Sri Lankan',
     NOW(), NOW()),
    ('Kamal Perera',
     'nimal@gmail.com',
     '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
     'TRAVELER',
     'LOCAL',
     'Sri Lankan',
     NOW(), NOW()),
    ('supun Perera',
     'supun@gmail.com',
     '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
     'TRAVELER',
     'LOCAL',
     'Sri Lankan',
     NOW(), NOW()),
    ('sadun Perera',
     'sadun@gmail.com',
     '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
     'TRAVELER',
     'LOCAL',
     'India',
     NOW(), NOW())
) AS new_data (name, email, password, role, auth_provider, nationality, created_at, updated_at)
WHERE NOT EXISTS (SELECT 1 FROM users LIMIT 1);

-- ─────────────────────────────────────────────────────────────
-- 2. Booking Status Schema Migration
-- ─────────────────────────────────────────────────────────────
UPDATE guide_bookings   SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';
UPDATE guide_bookings   SET status = 'CONFIRMED'       WHERE status = 'ADVANCE_PAID';
UPDATE guide_bookings   SET status = 'COMPLETED'       WHERE status = 'FULLY_PAID';
UPDATE vehicle_bookings SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';
UPDATE vehicle_bookings SET status = 'CONFIRMED'       WHERE status = 'ADVANCE_PAID';
UPDATE vehicle_bookings SET status = 'COMPLETED'       WHERE status = 'FULLY_PAID';

-- ─────────────────────────────────────────────────────────────
-- 3. Location Gazetteer Seed (lat/lng lookups for geocoding)
-- ─────────────────────────────────────────────────────────────
INSERT INTO locations (name, latitude, longitude) VALUES
    ('Colombo',       6.9271, 79.8612),
    ('Kandy',         7.2906, 80.6337),
    ('Galle',         6.0535, 80.2210),
    ('Ella',          6.8667, 81.0466),
    ('Mirissa',       5.9483, 80.4589),
    ('Nuwara Eliya',  6.9497, 80.7891),
    ('Jaffna',        9.6615, 80.0255),
    ('Trincomalee',   8.5874, 81.2152),
    ('Batticaloa',    7.7102, 81.6924),
    ('Anuradhapura',  8.3114, 80.4037),
    ('Polonnaruwa',   7.9403, 81.0188),
    ('Sigiriya',      7.9570, 80.7603),
    ('Dambulla',      7.8675, 80.6517),
    ('Negombo',       7.2083, 79.8358),
    ('Bentota',       6.4260, 79.9955),
    ('Hikkaduwa',     6.1408, 80.1018),
    ('Unawatuna',     6.0108, 80.2495),
    ('Tangalle',      6.0244, 80.7934),
    ('Arugam Bay',    6.8400, 81.8360),
    ('Yala',          6.3728, 81.5165),
    ('Udawalawe',     6.4675, 80.8974),
    ('Ratnapura',     6.6828, 80.4126),
    ('Kegalle',       7.2513, 80.3464),
    ('Kitulgala',     6.9908, 80.4197),
    ('Badulla',       6.9895, 81.0557),
    ('Haputale',      6.7676, 80.9564),
    ('Bandarawela',   6.8333, 80.9833),
    ('Matara',        5.9549, 80.5550),
    ('Kalutara',      6.5854, 79.9607),
    ('Beruwala',      6.4788, 79.9828)
ON CONFLICT (name) DO NOTHING;
