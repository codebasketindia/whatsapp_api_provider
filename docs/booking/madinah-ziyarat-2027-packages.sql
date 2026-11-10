-- HFHU booking set-up for the live link madinah-ziyarat-2027 (same as Flyway V11).
-- Safe to run more than once: packages are only added when that code is missing.

-- 1) Packages
INSERT INTO registration_packages (form_id, partner_id, code, name, description, icon, image_url, mrp, sale_price, mandatory, active,
                                   sort_order, pass_title, issue_pass, pass_style, created_at, updated_at)
SELECT f.id, f.partner_id, p.code, p.name, p.description, p.icon, p.image_url, p.mrp, p.sale_price, p.mandatory, TRUE,
       p.sort_order, p.pass_title, p.issue_pass, p.pass_style, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM registration_forms f
  JOIN (SELECT 'REG' code, 'Registration / Person — Sirf Ek Baar' name,
               'Free Pass ke liye bhi zaroori • Ek ya sabhi passes ke liye valid' description, 'ticket' icon, NULL image_url,
               49.00 mrp, 49.00 sale_price, TRUE mandatory, 0 sort_order, NULL pass_title, FALSE issue_pass, 'TICKET' pass_style
        UNION ALL SELECT 'HZS', 'Haramain Ziyarat Show Pass', 'Haramain ke andar aur aas-paas ki ziyarat', 'landmark',
               '/ziyarat/haramain-show.jpg', 0.00, 0.00, FALSE, 1, 'Haramain Ziyarat Show Pass', TRUE, 'FORM_DESIGN'
        UNION ALL SELECT 'SMM', 'Makkah–Madinah Khaas Ziyarat Pass', '2 Bus Rounds', 'moon-star',
               '/ziyarat/makkah-madinah-khaas.jpg', 6000.00, 3000.00, FALSE, 2, 'Makkah–Madinah Khaas Ziyarat Pass', TRUE, 'ZIYARAT_CARD'
        UNION ALL SELECT 'AMM', 'Makkah–Madinah Mazeed Ziyarat Pass', '2 Bus Rounds', 'map-pin',
               '/ziyarat/makkah-madinah-mazeed.jpg', 6000.00, 3000.00, FALSE, 3, 'Makkah–Madinah Mazeed Ziyarat Pass', TRUE, 'ZIYARAT_CARD'
        UNION ALL SELECT 'ACZ', 'Taif–Jeddah–Badr Ziyarat Pass', '3 Bus Rounds • Meals shamil', 'bus',
               '/ziyarat/taif-jeddah-badr.jpg', 8000.00, 4000.00, FALSE, 4, 'Taif–Jeddah–Badr Ziyarat Pass', TRUE, 'ZIYARAT_CARD') p
 WHERE f.slug = 'madinah-ziyarat-2027'
   AND NOT EXISTS (SELECT 1 FROM registration_packages x WHERE x.form_id = f.id AND x.code = p.code);

-- 2) Page look + combo offer
UPDATE registration_forms
   SET title = 'Ziyarat Passes for HAJJ 2027',
       description = 'Haramain Sharifain ke atraaf ki Ziyarat - Hajj 2027 ke Indian Hujjaj ke liye.',
       hero_image_url = '/ziyarat/hajj-2027-banner.jpg', page_heading = 'Apna Ziyarat Pass Chuniye',
       page_note = 'Paid pass rates Free Pass holders ke liye', cta_label = 'Register Karein',
       combo_title = 'Teeno Paid Pass • 7 Bus Rounds', combo_subtitle = 'Limited Seats • 15 December 2026 tak',
       combo_codes = 'SMM,AMM,ACZ', combo_mrp = 20000.00, combo_price = 9000.00,
       combo_valid_until = '2026-12-15 18:29:59.999999', updated_at = UTC_TIMESTAMP(6)
 WHERE slug = 'madinah-ziyarat-2027';

-- 3) Invoice name (needs the invoice_name column from V10)
UPDATE registration_forms SET invoice_name = 'HFHU Vision Private Limited' WHERE slug = 'madinah-ziyarat-2027';

-- Check
SELECT p.code, p.name, p.mrp, p.sale_price, p.mandatory, p.issue_pass, p.pass_style
  FROM registration_packages p JOIN registration_forms f ON f.id = p.form_id
 WHERE f.slug = 'madinah-ziyarat-2027' ORDER BY p.sort_order;
