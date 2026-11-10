-- Link preview (WhatsApp / Facebook / Telegram) for each booking link: title, description and image.
ALTER TABLE registration_forms
    ADD COLUMN share_title       VARCHAR(160) NULL,
    ADD COLUMN share_description VARCHAR(300) NULL,
    ADD COLUMN share_image_url   VARCHAR(500) NULL;

UPDATE registration_forms
   SET share_title = 'Har Fard Hajj Umrah',
       share_description = 'HFHU Vision Private Limited • Ziyarat Passes for Hajj 2027',
       share_image_url = '/ziyarat/share-hajj-2027.jpg'
 WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024');
