-- Seller name printed on booking invoices (defaults to the partner's business name).
ALTER TABLE registration_forms ADD COLUMN invoice_name VARCHAR(160) NULL;

-- HFHU Ziyarat link: invoices in the company's name, paid passes on the green/gold Ziyarat card.
UPDATE registration_forms SET invoice_name = 'HFHU Vision Private Limited' WHERE slug = 'madinah-ziyarat-2024';
UPDATE registration_packages p JOIN registration_forms f ON f.id = p.form_id
   SET p.pass_style = 'ZIYARAT_CARD'
 WHERE f.slug = 'madinah-ziyarat-2024' AND p.code IN ('SMM', 'AMM', 'ACZ');
