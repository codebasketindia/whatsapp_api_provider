-- Bookings with packages (v1.7): several paid packages per registration link, one QR pass per package,
-- invoice, pass verification / redemption. Links without packages keep working as before.

CREATE TABLE registration_packages (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    form_id             BIGINT UNSIGNED NOT NULL,
    partner_id          BIGINT UNSIGNED NOT NULL,
    code                VARCHAR(20)   NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    description         VARCHAR(1000) NULL,
    details             TEXT          NULL,
    icon                VARCHAR(40)   NULL,
    image_file_id       BIGINT UNSIGNED NULL,
    mrp                 DECIMAL(12,2) NOT NULL,
    sale_price          DECIMAL(12,2) NOT NULL,
    mandatory           BOOLEAN       NOT NULL DEFAULT FALSE,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order          INT           NOT NULL DEFAULT 0,
    pass_title          VARCHAR(160)  NULL,
    pass_design_file_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_reg_packages_code UNIQUE (form_id, code),
    CONSTRAINT fk_rp_form FOREIGN KEY (form_id) REFERENCES registration_forms (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_image FOREIGN KEY (image_file_id) REFERENCES stored_files (id),
    CONSTRAINT fk_rp_design FOREIGN KEY (pass_design_file_id) REFERENCES stored_files (id)
) ENGINE=InnoDB;

CREATE TABLE registration_items (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT UNSIGNED NOT NULL,
    package_id      BIGINT UNSIGNED NOT NULL,
    partner_id      BIGINT UNSIGNED NOT NULL,
    name            VARCHAR(120)  NOT NULL,
    mrp             DECIMAL(12,2) NOT NULL,
    price           DECIMAL(12,2) NOT NULL,
    pass_no         VARCHAR(40)   NULL,
    pass_file_id    BIGINT UNSIGNED NULL,
    redeemed_at     DATETIME(6)   NULL,
    redeemed_by     BIGINT UNSIGNED NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uk_reg_items_pass UNIQUE (pass_no),
    CONSTRAINT fk_ri_registration FOREIGN KEY (registration_id) REFERENCES registrations (id) ON DELETE CASCADE,
    CONSTRAINT fk_ri_package FOREIGN KEY (package_id) REFERENCES registration_packages (id),
    CONSTRAINT fk_ri_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_ri_pass FOREIGN KEY (pass_file_id) REFERENCES stored_files (id),
    INDEX idx_ri_registration (registration_id)
) ENGINE=InnoDB;

ALTER TABLE registration_forms
    ADD COLUMN terms_text   LONGTEXT NULL,
    ADD COLUMN privacy_text LONGTEXT NULL;

ALTER TABLE registrations
    ADD COLUMN invoice_no VARCHAR(40)   NULL,
    ADD COLUMN mrp_total  DECIMAL(12,2) NULL,
    ADD CONSTRAINT uk_registrations_invoice UNIQUE (invoice_no);

-- The Hajj 2027 Ziyarat link gets its packages (only if that link exists and has none yet).
UPDATE registration_forms SET title = 'Ziyarat Passes for HAJJ 2027'
 WHERE slug = 'madinah-ziyarat-2024';
INSERT INTO registration_packages (form_id, partner_id, code, name, description, icon, mrp, sale_price, mandatory, active, sort_order, pass_title, created_at, updated_at)
SELECT f.id, f.partner_id, p.code, p.name, p.description, p.icon, p.mrp, p.sale_price, p.mandatory, TRUE, p.sort_order, p.pass_title,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM registration_forms f
  JOIN (SELECT 'REG' code, 'Harmain Ziyarat Show' name,
               'Registration fee - includes the Harmain Ziyarat Show pass.' description, 'landmark' icon,
               49.00 mrp, 49.00 sale_price, TRUE mandatory, 1 sort_order, 'Harmain Ziyarat Show Pass' pass_title
        UNION ALL SELECT 'SMM', 'Special Makkah & Madina Ziyarat', 'Guided Ziyarat of the special historic places of Makkah and Madina.',
               'moon-star', 5000.00, 3000.00, FALSE, 2, 'Special Makkah & Madina Ziyarat Pass'
        UNION ALL SELECT 'AMM', 'Additional Makkah & Madina Ziyarat', 'More Ziyarat places in Makkah and Madina on additional days.',
               'map-pin', 5000.00, 3000.00, FALSE, 3, 'Additional Makkah & Madina Ziyarat Pass'
        UNION ALL SELECT 'ACZ', 'Additional Cities Ziyarat', 'Ziyarat trips to other historic cities.',
               'bus', 5000.00, 3000.00, FALSE, 4, 'Additional Cities Ziyarat Pass') p
 WHERE f.slug = 'madinah-ziyarat-2024'
   AND NOT EXISTS (SELECT 1 FROM registration_packages x WHERE x.form_id = f.id);
