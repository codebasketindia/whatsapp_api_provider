-- HFHU links: combo offer back on - the three paid passes together cost Rs 9,000 (Rs 1,000 off their Rs 10,000 sum).
UPDATE registration_forms SET combo_price = 9000.00, updated_at = UTC_TIMESTAMP(6)
 WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024');
