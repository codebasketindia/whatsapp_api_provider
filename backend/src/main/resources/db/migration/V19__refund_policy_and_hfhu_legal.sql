-- Refund Policy page for booking links, next to Terms & Conditions and Privacy Policy.
ALTER TABLE registration_forms ADD COLUMN refund_text LONGTEXT NULL;

-- HFHU links: starting texts for the three pages. Only filled where empty, so text a partner wrote is never replaced.
-- The partner reviews and edits them in the link editor (Booking packages -> Terms / Privacy / Refund).
UPDATE registration_forms SET terms_text =
'1. These passes are for Indian Hujjaj travelling for Hajj 2027. The programme is run by HFHU Vision Pvt Ltd.

2. A one-time registration fee of Rs 49 per person is required for every booking, including the free Haramain Ziyarat Show Pass. It is valid for one or all passes of the same person.

3. Each pass is issued in the name and mobile number given at booking. Passes are personal and cannot be transferred or resold.

4. Every pass has its own QR code. Show the QR code at the entry; each QR code can be used only once. A copied, edited or already used pass will not be accepted.

5. Bus rounds, places visited, timings and meals are as listed under Full Details for each pass. The route and timings may change because of traffic, weather, crowd control or instructions of the authorities.

6. The programme is subject to the applicable rules, permissions and directions of the Government of India and the authorities of Saudi Arabia. HFHU Vision Pvt Ltd may change or cancel a round if these require it.

7. Pilgrims must follow the instructions of the group leader, reach the pick-up point on time and keep their own documents and belongings safe. A missed round because of late arrival is not repeated.

8. Combo and special prices are valid only for the period shown on the booking page and only when all the passes of the combo are bought together.

9. Payments are made online through Razorpay. No GST or other tax is charged on these passes.

10. Refunds follow the Refund Policy on this page.

11. For help, contact the Office: +91 9867621786, or Emergency Support: +966590550490.'
WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024') AND (terms_text IS NULL OR terms_text = '');

UPDATE registration_forms SET privacy_text =
'HFHU Vision Pvt Ltd collects only what is needed to book and run your Ziyarat passes:

- Your name and WhatsApp mobile number, to issue your passes, print them on the passes and invoice, and send them to you on WhatsApp.
- The passes you choose, your booking number and the payment reference from Razorpay, to confirm the booking and check your passes at the entry.

Card, UPI and bank details are entered on Razorpay''s secure checkout and are not stored by us.

We use your WhatsApp number to send your passes, booking updates and programme information. You can ask us to stop messages at any time.

Your details are shared only with the staff and partners who run the programme, with Razorpay to process the payment, and with the authorities when the law requires it. We do not sell your data.

Booking records are kept as long as needed for the programme, accounts and the law.

To see, correct or delete your details, contact the Office: +91 9867621786.'
WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024') AND (privacy_text IS NULL OR privacy_text = '');

UPDATE registration_forms SET refund_text =
'1. If you cancel a package (pass), its amount is refunded within 7 working days.

2. The Rs 49 registration fee is not refundable.

3. To cancel, send your booking number and pass ID on WhatsApp to the Office: +91 9867621786.

4. The refund is paid back to the same payment method (UPI, card or bank account) through Razorpay.'
WHERE slug IN ('madinah-ziyarat-2027', 'madinah-ziyarat-2024') AND (refund_text IS NULL OR refund_text = '');
