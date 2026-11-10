-- HFHU links: the three paid passes together cost their normal sum (Rs 10,000), no combo discount.
UPDATE registration_forms SET combo_price = 10000.00, updated_at = UTC_TIMESTAMP(6)
 WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024');
