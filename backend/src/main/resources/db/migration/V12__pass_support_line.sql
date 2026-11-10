-- Small line printed at the bottom of Ziyarat-card passes (e.g. an emergency number).
ALTER TABLE registration_forms ADD COLUMN pass_support_line VARCHAR(120) NULL;

UPDATE registration_forms SET pass_support_line = 'Emergency Support : +966590550490'
 WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024');
