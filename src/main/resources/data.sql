INSERT INTO vehicles (name, type, brand, model, year, seats, color,
    price_per_day, currency, district, pickup_location,
    driver_included, airport_transfer, available,
    driver_name, driver_phone, driver_languages,
    description, rating, review_count, category,
    created_at, updated_at)
VALUES
('Colombo City Tuk-Tuk', 'TUKTUK', 'Bajaj', 'RE', 2022, 3, 'Yellow',
    15.00, 'USD', 'Colombo', 'Colombo Fort',
    true, false, true,
    'Kamal Silva', '+94771234567', 'English, Sinhala',
    'Reliable tuk-tuk service around Colombo city', 4.5, 120, 'LOCAL',
    NOW(), NOW()),

('Airport Transfer Van', 'VAN', 'Toyota', 'HiAce', 2021, 8, 'White',
    45.00, 'USD', 'Colombo', 'BIA Katunayake Airport',
    true, true, true,
    'Nimal Perera', '+94777654321', 'English, Sinhala, Tamil',
    'Comfortable airport transfer van with AC', 4.8, 85, 'STANDARD',
    NOW(), NOW()),

('Kandy Hill Country Car', 'CAR', 'Toyota', 'Prius', 2020, 4, 'Silver',
    35.00, 'USD', 'Kandy', 'Kandy City Center',
    true, false, true,
    'Sunil Fernando', '+94765432109', 'English, Sinhala',
    'Comfortable car for hill country exploration', 4.6, 67, 'STANDARD',
    NOW(), NOW()),

('Ella Adventure Jeep', 'SUV', 'Jeep', 'Wrangler', 2019, 5, 'Green',
    55.00, 'USD', 'Badulla', 'Ella Town',
    true, false, true,
    'Roshan Dias', '+94712345678', 'English',
    'Perfect for Ella Rock and Nine Arch Bridge tours', 4.9, 43, 'PREMIUM',
    NOW(), NOW()),

('Galle Southern Tuk-Tuk', 'TUKTUK', 'Bajaj', 'RE', 2023, 3, 'Blue',
    12.00, 'USD', 'Galle', 'Galle Fort',
    true, false, true,
    'Priya Cooray', '+94723456789', 'English, Sinhala',
    'Best tuk-tuk for Galle Fort and southern coast', 4.4, 98, 'LOCAL',
    NOW(), NOW()),

('Colombo Luxury SUV', 'SUV', 'Toyota', 'Land Cruiser', 2022, 6, 'Black',
    85.00, 'USD', 'Colombo', 'Colombo 03',
    true, false, true,
    'Asanka Mendis', '+94734567890', 'English, Sinhala',
    'Premium SUV for luxury travel around Sri Lanka', 4.7, 32, 'LUXURY',
    NOW(), NOW()),

('Mirissa Beach Scooter', 'SCOOTER', 'Honda', 'Wave', 2021, 2, 'Red',
    18.00, 'USD', 'Matara', 'Mirissa Beach',
    false, false, true,
    NULL, NULL, NULL,
    'Self-drive scooter perfect for coastal exploration', 4.3, 156, 'ECONOMY',
    NOW(), NOW()),

('Sigiriya Cultural Van', 'MINIVAN', 'Toyota', 'KDH', 2020, 10, 'White',
    60.00, 'USD', 'Matale', 'Sigiriya Rock',
    true, false, true,
    'Chaminda Rathnayake', '+94745678901', 'English, Sinhala, Japanese',
    'Perfect for cultural triangle tours', 4.8, 54, 'STANDARD',
    NOW(), NOW());


INSERT INTO events (title, description, category, region,
    start_date, end_date, image_url, is_recurring, created_at)
VALUES
('Vesak Festival',
 'Sri Lanka largest Buddhist festival celebrating the birth, enlightenment and passing of Lord Buddha. Lanterns and pandols lit across the island.',
 'FESTIVAL', 'Island-wide', '2026-05-12', '2026-05-13', NULL, true, NOW()),

('Esala Perahera',
 'Grand procession of the Temple of the Tooth Relic in Kandy. Elephants, drummers, fire dancers in spectacular parade.',
 'FESTIVAL', 'Kandy', '2026-08-03', '2026-08-13', NULL, true, NOW()),

('Sinhala and Tamil New Year',
 'Traditional Sri Lankan New Year celebrated by both Sinhalese and Tamil communities with rituals, games and special foods.',
 'FESTIVAL', 'Island-wide', '2026-04-13', '2026-04-14', NULL, true, NOW()),

('Thai Pongal',
 'Tamil harvest festival giving thanks to the Sun God. Traditional Pongal dish cooked and celebrations with kolam drawings.',
 'RELIGIOUS', 'Jaffna', '2026-01-14', '2026-01-15', NULL, true, NOW()),

('Kataragama Festival',
 'Major religious festival at the Kataragama temple complex attended by Buddhist, Hindu, Muslim and indigenous Vedda devotees.',
 'RELIGIOUS', 'Kataragama', '2026-07-28', '2026-08-06', NULL, true, NOW()),

('Elephant Gathering — Minneriya',
 'One of the largest gatherings of wild Asian elephants in the world. Hundreds of elephants congregate at Minneriya Tank.',
 'WILDLIFE', 'Minneriya', '2026-07-01', '2026-09-30', NULL, true, NOW()),

('Whale Watching Season — Mirissa',
 'Peak season for blue whale and sperm whale sightings off the southern coast of Sri Lanka. Best months November to April.',
 'WILDLIFE', 'Mirissa', '2026-11-01', '2027-04-30', NULL, true, NOW()),

('Arugam Bay Surf Season',
 'World-class surf season at Arugam Bay. Attracts international surfers. Main Point and Whiskey Point are top surf spots.',
 'SURF', 'Arugam Bay', '2026-05-01', '2026-10-31', NULL, true, NOW()),

('Galle Literary Festival',
 'International literary festival held inside the historic Galle Fort. Authors, speakers and cultural events.',
 'ENTERTAINMENT', 'Galle', '2026-01-20', '2026-01-24', NULL, true, NOW()),

('Colombo Food Festival',
 'Annual food festival showcasing Sri Lankan cuisine, street food, and international dishes in Colombo.',
 'FOOD', 'Colombo', '2026-03-15', '2026-03-17', NULL, true, NOW()),

('Southwest Monsoon',
 'Southwest monsoon brings heavy rainfall to western and southern coastal areas and hill country. Best to visit east coast during this period.',
 'MONSOON', 'West and South Coast', '2026-05-01', '2026-09-30', NULL, true, NOW()),

('Northeast Monsoon',
 'Northeast monsoon affects northern and eastern coastal areas. Best to visit west and south coast during this period.',
 'MONSOON', 'North and East Coast', '2026-10-01', '2027-01-31', NULL, true, NOW()),

('Diwali — Festival of Lights',
 'Hindu festival of lights celebrated by Tamil community across Sri Lanka with oil lamps, fireworks and sweets.',
 'RELIGIOUS', 'Island-wide', '2026-10-20', '2026-10-20', NULL, true, NOW()),

('Christmas in Galle Fort',
 'Unique Christmas celebrations inside the historic Galle Fort with decorations, events and festive markets.',
 'FESTIVAL', 'Galle', '2026-12-24', '2026-12-26', NULL, true, NOW()),

('Hikkaduwa Coral Festival',
 'Annual festival celebrating the coral reef ecosystem of Hikkaduwa. Snorkeling, diving events and beach activities.',
 'ENTERTAINMENT', 'Hikkaduwa', '2026-03-01', '2026-03-03', NULL, true, NOW());