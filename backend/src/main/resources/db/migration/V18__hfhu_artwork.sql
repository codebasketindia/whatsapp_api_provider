-- HFHU artwork: "Special Offer" banner on the booking page, PASS 1-4 pass designs, HFHU invoice layout.
ALTER TABLE registration_forms
    ADD COLUMN invoice_style   VARCHAR(16)  NULL,
    ADD COLUMN invoice_contact VARCHAR(160) NULL;

ALTER TABLE registration_packages MODIFY COLUMN pass_style VARCHAR(16) NOT NULL DEFAULT 'TICKET';

UPDATE registration_forms
   SET hero_image_url = '/ziyarat/banner-special-offer.jpg', invoice_style = 'HFHU',
       invoice_contact = 'Office: +91 9867621786', updated_at = UTC_TIMESTAMP(6)
 WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024');

UPDATE registration_packages p JOIN registration_forms f ON f.id = p.form_id
   SET p.pass_style = CASE p.code WHEN 'HZS' THEN 'HFHU_1' WHEN 'SMM' THEN 'HFHU_2' WHEN 'AMM' THEN 'HFHU_3' WHEN 'ACZ' THEN 'HFHU_4' END,
       p.updated_at = UTC_TIMESTAMP(6)
 WHERE f.slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024') AND p.code IN ('HZS', 'SMM', 'AMM', 'ACZ');
