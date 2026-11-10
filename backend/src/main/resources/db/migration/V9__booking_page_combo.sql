-- Booking page look (hero, heading, note, button) + combo offer + package picture URL / free pass / pass style (v1.8)

ALTER TABLE registration_forms
    ADD COLUMN hero_image_url    VARCHAR(500)  NULL,
    ADD COLUMN page_heading      VARCHAR(160)  NULL,
    ADD COLUMN page_note         VARCHAR(300)  NULL,
    ADD COLUMN cta_label         VARCHAR(60)   NULL,
    ADD COLUMN combo_title       VARCHAR(160)  NULL,
    ADD COLUMN combo_subtitle    VARCHAR(200)  NULL,
    ADD COLUMN combo_codes       VARCHAR(200)  NULL,
    ADD COLUMN combo_mrp         DECIMAL(12,2) NULL,
    ADD COLUMN combo_price       DECIMAL(12,2) NULL,
    ADD COLUMN combo_valid_until DATETIME(6)   NULL;

ALTER TABLE registration_packages
    ADD COLUMN image_url  VARCHAR(500) NULL,
    ADD COLUMN issue_pass BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN pass_style VARCHAR(16)  NOT NULL DEFAULT 'TICKET';

ALTER TABLE registrations
    ADD COLUMN discount_amount DECIMAL(12,2) NULL,
    ADD COLUMN discount_label  VARCHAR(160)  NULL;

-- madinah-ziyarat-2024: the HFHU "Apna Ziyarat Pass Chuniye" page
UPDATE registration_forms
   SET title = 'Ziyarat Passes for HAJJ 2027', hero_image_url = '/ziyarat/hajj-2027-banner.jpg',
       page_heading = 'Apna Ziyarat Pass Chuniye', page_note = 'Paid pass rates Free Pass holders ke liye',
       cta_label = 'Register Karein', combo_title = 'Teeno Paid Pass • 7 Bus Rounds',
       combo_subtitle = 'Limited Seats • 15 December 2026 tak', combo_codes = 'SMM,AMM,ACZ',
       combo_mrp = 20000.00, combo_price = 9000.00, combo_valid_until = '2026-12-15 18:29:59.999999'
 WHERE slug = 'madinah-ziyarat-2024';

UPDATE registration_packages p JOIN registration_forms f ON f.id = p.form_id
   SET p.name = 'Registration / Person — Sirf Ek Baar', p.description = 'Free Pass ke liye bhi zaroori • Ek ya sabhi passes ke liye valid',
       p.mrp = 49.00, p.sale_price = 49.00, p.mandatory = TRUE, p.issue_pass = FALSE, p.icon = 'ticket', p.sort_order = 0,
       p.pass_title = NULL, p.updated_at = UTC_TIMESTAMP(6)
 WHERE f.slug = 'madinah-ziyarat-2024' AND p.code = 'REG';
UPDATE registration_packages p JOIN registration_forms f ON f.id = p.form_id
   SET p.name = 'Makkah–Madinah Khaas Ziyarat Pass', p.description = '2 Bus Rounds', p.mrp = 6000.00, p.sale_price = 3000.00,
       p.image_url = '/ziyarat/makkah-madinah-khaas.jpg', p.sort_order = 2, p.pass_title = 'Makkah–Madinah Khaas Ziyarat Pass', p.updated_at = UTC_TIMESTAMP(6)
 WHERE f.slug = 'madinah-ziyarat-2024' AND p.code = 'SMM';
UPDATE registration_packages p JOIN registration_forms f ON f.id = p.form_id
   SET p.name = 'Makkah–Madinah Mazeed Ziyarat Pass', p.description = '2 Bus Rounds', p.mrp = 6000.00, p.sale_price = 3000.00,
       p.image_url = '/ziyarat/makkah-madinah-mazeed.jpg', p.sort_order = 3, p.pass_title = 'Makkah–Madinah Mazeed Ziyarat Pass', p.updated_at = UTC_TIMESTAMP(6)
 WHERE f.slug = 'madinah-ziyarat-2024' AND p.code = 'AMM';
UPDATE registration_packages p JOIN registration_forms f ON f.id = p.form_id
   SET p.name = 'Taif–Jeddah–Badr Ziyarat Pass', p.description = '3 Bus Rounds • Meals shamil', p.mrp = 8000.00, p.sale_price = 4000.00,
       p.image_url = '/ziyarat/taif-jeddah-badr.jpg', p.sort_order = 4, p.pass_title = 'Taif–Jeddah–Badr Ziyarat Pass', p.updated_at = UTC_TIMESTAMP(6)
 WHERE f.slug = 'madinah-ziyarat-2024' AND p.code = 'ACZ';
INSERT INTO registration_packages (form_id, partner_id, code, name, description, icon, image_url, mrp, sale_price, mandatory, active,
                                   sort_order, pass_title, issue_pass, pass_style, created_at, updated_at)
SELECT f.id, f.partner_id, 'HZS', 'Haramain Ziyarat Show Pass', 'Haramain ke andar aur aas-paas ki ziyarat', 'landmark',
       '/ziyarat/haramain-show.jpg', 0.00, 0.00, FALSE, TRUE, 1, 'Haramain Ziyarat Show Pass', TRUE, 'FORM_DESIGN',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM registration_forms f
 WHERE f.slug = 'madinah-ziyarat-2024'
   AND NOT EXISTS (SELECT 1 FROM registration_packages x WHERE x.form_id = f.id AND x.code = 'HZS');
