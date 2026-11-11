-- Add-on purchases: the same mobile number buys more passes later; they join the same booking.
-- Each add-on payment is one booking_orders row with its own Razorpay order and invoice.
CREATE TABLE booking_orders (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    registration_id     BIGINT UNSIGNED NOT NULL,
    partner_id          BIGINT UNSIGNED NOT NULL,
    seq                 INT           NOT NULL,
    package_ids         VARCHAR(500)  NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    mrp_total           DECIMAL(12,2) NOT NULL,
    discount_amount     DECIMAL(12,2) NULL,
    discount_label      VARCHAR(160)  NULL,
    invoice_no          VARCHAR(40)   NULL,
    razorpay_order_id   VARCHAR(64)   NULL,
    razorpay_payment_id VARCHAR(64)   NULL,
    payment_method      VARCHAR(40)   NULL,
    paid_at             DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT uk_bo_seq UNIQUE (registration_id, seq),
    CONSTRAINT uk_bo_rzp_order UNIQUE (razorpay_order_id),
    CONSTRAINT uk_bo_invoice UNIQUE (invoice_no),
    CONSTRAINT fk_bo_registration FOREIGN KEY (registration_id) REFERENCES registrations (id) ON DELETE CASCADE,
    CONSTRAINT fk_bo_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    INDEX idx_bo_status (status, updated_at)
) ENGINE=InnoDB;

-- Items bought in an add-on point to its order (NULL = the first booking). One package once per booking.
ALTER TABLE registration_items
    ADD COLUMN order_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_ri_order FOREIGN KEY (order_id) REFERENCES booking_orders (id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_ri_reg_package UNIQUE (registration_id, package_id);
