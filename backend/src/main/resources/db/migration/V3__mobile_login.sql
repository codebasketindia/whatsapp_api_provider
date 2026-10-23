-- Mobile number becomes the login id for every user (admins, staff, partners).
-- Normalise to WhatsApp/E.164 digits without "+" (e.g. 919876543210) and make it unique.
UPDATE users SET mobile = NULL WHERE mobile IS NOT NULL AND TRIM(mobile) = '';
UPDATE users SET mobile = REGEXP_REPLACE(mobile, '[^0-9]', '') WHERE mobile IS NOT NULL;
UPDATE users SET mobile = SUBSTRING(mobile, 2) WHERE mobile IS NOT NULL AND CHAR_LENGTH(mobile) = 11 AND mobile LIKE '0%';
UPDATE users SET mobile = CONCAT('91', mobile) WHERE mobile IS NOT NULL AND CHAR_LENGTH(mobile) = 10;
-- Duplicates (if any) keep the number on the oldest user only; the others must set a new one.
UPDATE users u JOIN (SELECT mobile, MIN(id) keep_id FROM users WHERE mobile IS NOT NULL GROUP BY mobile HAVING COUNT(*) > 1) d
    ON u.mobile = d.mobile AND u.id <> d.keep_id
   SET u.mobile = NULL;
UPDATE partners SET mobile = REGEXP_REPLACE(mobile, '[^0-9]', '');
UPDATE partners SET mobile = SUBSTRING(mobile, 2) WHERE CHAR_LENGTH(mobile) = 11 AND mobile LIKE '0%';
UPDATE partners SET mobile = CONCAT('91', mobile) WHERE CHAR_LENGTH(mobile) = 10;
ALTER TABLE users ADD CONSTRAINT uk_users_mobile UNIQUE (mobile);
