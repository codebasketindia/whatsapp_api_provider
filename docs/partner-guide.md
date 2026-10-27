# Partner Guide — Sending WhatsApp messages with CB WhatsApp Gateway

## 1. Log in
Log in with your **mobile number** and the temporary password from your welcome email. You will be asked to set
your own password. Forgot it? Tap *Forgot password*, enter your mobile number and use the code sent to your email.

## 2. Add money
**Wallet → Add money** (or the green button at the bottom on mobile):
- **Pay by UPI** — enter an amount, pay with any UPI app (or card/net banking). Your wallet is updated in a few
  seconds. GST (if applicable) is shown before you pay and an invoice is created automatically.
- **Buy a pack** — pay a fixed amount and get bonus credit (e.g. pay ₹1,000, get ₹1,100).
- **I already paid** — paid by bank transfer/UPI to our account? Enter the amount, UTR/reference number, date and
  upload the screenshot. We approve it after checking; you get an email.

**Passbook** shows every rupee: credits, message charges, money on hold and refunds.
Set your **low balance alert** — we email you when the balance drops below it.

## 3. Your WhatsApp number
**My senders** shows numbers we gave you and your own numbers.
To connect **your own number**: *Connect WhatsApp Number* → choose
- *New number* (not on WhatsApp yet),
- *Existing WhatsApp Business API number*, or
- *Keep using the WhatsApp Business App* (coexistence: use the app and the API together),
then set a 6-digit PIN and log in with Facebook. After connecting, follow the **What to do next** list (for
example add a payment method in WhatsApp Manager or wait for your display name approval).

## 4. Templates (needed to start a conversation)
WhatsApp only lets businesses start a chat with an approved **template**. **Templates → New**:
- **Marketing** (offers, news), **Utility** (order/account updates), **Authentication** (OTP codes).
- Write the message, add variables like `{{1}}` and give sample values, add buttons. The preview shows exactly
  how it looks. Submit — approval usually takes minutes to a few hours.

## 5. Send a message
**Send** → choose your number → enter the recipient → choose a template and fill the variables → check the
**cost** → Send. Ticks show the status: ✓ sent, ✓✓ delivered, blue ✓✓ read.

**24-hour window**: after a customer messages you, you can reply with normal text, photos and documents for 24
hours (cheaper / free from Meta). Outside the window you must use a template.

## 6. Bulk campaigns
**Campaigns → New**: upload a CSV/Excel file (or pick contact groups) → match columns (phone number and template
variables) → see how many are valid, skipped (invalid, duplicate, opted out) and the total cost → send now or
schedule. Watch live progress; pause or cancel any time; download the report. Failed messages are refunded
(when the platform charges on delivery).

## 7. Contacts, opt-outs and inbox
- **Contacts**: add, import, tag and group your customers.
- Customers who reply **STOP** or **UNSUBSCRIBE** are opted out of marketing automatically (edit the keywords in
  Contacts → Opt-outs). You can block numbers from the number's Block list.
- **Inbox**: see replies, photos and documents from customers and answer them.

## 8. AI Assistant (automatic replies)
**AI Assistant** answers your customers' WhatsApp messages for you — **only from the information you give it**.
1. **Knowledge** tab → *Add*: one item per topic (menu & prices, timings, address, delivery areas, offers, FAQ…).
   Attach a photo, video or PDF to an item (e.g. the menu card) and the AI can send it when it is relevant.
2. **Setup** tab: business name, extra instructions (e.g. "always mention free delivery above ₹500"), language
   (Auto / English / Hindi / Hinglish), tone, **fallback message** (sent when the answer is not in your knowledge,
   e.g. "Our team will reply shortly") and **handover words** (e.g. *agent*, *human*, *complaint*). Choose all numbers
   or only some, then switch it **On**.
3. **Test** tab: chat with your AI before customers do (free, 100 tests a day).

How it behaves:
- It never makes up prices, offers or policies. If the answer is not in your knowledge it sends your fallback
  message, **stops replying in that chat** and marks it **Needs you** in the Inbox. Switch *AI replies* back on in
  that chat when you are done.
- It replies only inside the 24-hour window (free from Meta) and ignores STOP/START words.
- Each AI reply costs a small fee (shown on the Setup tab) from your wallet. Low balance → the AI stays quiet and
  the chat is marked for you. **Usage** tab shows every reply, fallback and cost.

## 9. Scheduled messages
**Scheduled** → *New scheduled message*: choose number and customer, pick date & time (India time, 1 minute to
90 days ahead) and write the message — text, photo, video, document or an approved template. You can also schedule
from a chat (clock button in the composer).
- **Edit**, **Change time** (reschedule), **Send now** or **Cancel** while it is still *Upcoming*.
- Money is taken only when it is actually sent. Normal text/photo messages need the customer's 24-hour window to be
  open at that time — otherwise it fails with a clear reason; use a template for anything later.
- Scheduled **campaigns** can also be moved: Campaign → *Change time*.

## 10. Developers (API)
**Developer** page: create API keys (the secret is shown once — save it), add your server IPs for extra safety,
set a webhook URL to receive delivery status and incoming messages, send a test event and copy code samples in
cURL, PHP, Node.js, Java and Python. Full reference: `docs/api-guide.md`.
