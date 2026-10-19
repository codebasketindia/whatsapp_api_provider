# Deployment Guide — Ubuntu VPS (no Docker)

[QA & DEVOPS ENGINEER] · Target: Ubuntu 22.04 / 24.04, 2 vCPU / 4 GB RAM minimum, a domain pointing to the server.

```
Internet ──443──► Nginx (TLS, Let's Encrypt)
                   ├── /            → /var/www/cbwa        (React build)
                   ├── /api/**      → 127.0.0.1:8080       (Spring Boot JAR, systemd "cbwa")
                   └── /actuator/health
                  MySQL 8 on 127.0.0.1:3306 · uploads in /var/lib/cbwa/storage · logs in /var/log/cbwa
```

## 1. Prepare DNS
Create an `A` record `wa.example.com → <server IP>` and wait until `dig +short wa.example.com` returns the IP.

## 2. One-time server setup
```bash
sudo apt-get update && sudo apt-get install -y git
git clone <your repo url> /home/ubuntu/cbwa && cd /home/ubuntu/cbwa
sudo DOMAIN=wa.example.com EMAIL=you@example.com ./deploy/scripts/server-setup.sh
```
The script installs Java 21, MySQL 8, Nginx, Certbot, Node.js (build only), creates the `cbwa` system user,
directories, firewall rules (SSH + 80/443 only), the database and user, the systemd unit, the Nginx site with a
Let's Encrypt certificate (auto-renew timer), cron jobs for backups and health checks, and
`/etc/cbwa/cbwa.env` with freshly generated JWT secret, encryption key and API pepper.

## 3. Finish configuration
```bash
sudo mysql -e "ALTER USER 'cbwa_app'@'localhost' IDENTIFIED BY '<strong-db-password>';"
sudo mysql -e "ALTER USER 'cbwa_backup'@'localhost' IDENTIFIED BY '<strong-backup-password>';"
sudo nano /etc/cbwa/cbwa.env      # CBWA_DB_PASSWORD, CBWA_SEED_ADMIN_EMAIL, CBWA_SEED_ADMIN_PASSWORD
printf "[client]\nuser=cbwa_backup\npassword=<strong-backup-password>\n" | sudo tee /etc/cbwa/backup.cnf
printf "[client]\nuser=root\n" | sudo tee /etc/cbwa/restore.cnf
sudo chmod 600 /etc/cbwa/backup.cnf /etc/cbwa/restore.cnf
```
**Back up `CBWA_ENCRYPTION_KEY` in a password manager.** Without it, saved Meta tokens, PINs and gateway keys
cannot be decrypted.

## 4. Deploy (first time and every update)
```bash
cd /home/ubuntu/cbwa && git pull
./deploy/scripts/deploy.sh
```
Builds the JAR (`backend/target/cbwa.jar`) and the React app (`frontend/dist`), backs up the DB, installs both,
restarts `cbwa` and waits for `/actuator/health` = UP. Flyway applies database migrations automatically on start.

Useful commands:
```bash
sudo systemctl status cbwa          # service state
sudo journalctl -u cbwa -f          # live logs (also /var/log/cbwa/cbwa.log, JSON)
curl -s https://wa.example.com/actuator/health
```

## 5. First login
Open `https://wa.example.com/login`, log in with the seed admin email/password and choose a new password.
Then in **Settings**: Company (name, GSTIN, state, logo), SMTP (send a test email), GST & invoice, Charging rule,
Payment gateway, API limits. In **Pricing → Rate plans** enter India prices for the default plan
(sending is blocked until prices exist). Activate the credit packs you want to sell.

## 6. Meta (WhatsApp Cloud API) configuration
1. developers.facebook.com → your Business app → **App settings → Basic**: App domain = your domain, Privacy
   policy URL, Terms URL, Data deletion URL. Copy **App ID** and **App secret**.
2. **WhatsApp → Configuration → Webhook**: Callback URL `https://wa.example.com/api/webhooks/whatsapp`,
   Verify token = any long random string. In the admin panel **WhatsApp → Platform settings** paste App ID, App
   secret, the same verify token and Graph API version (e.g. `v25.0`), save, then click **Verify and save** in Meta.
3. Subscribe webhook fields: `messages, message_template_status_update, message_template_quality_update,
   message_template_components_update, template_category_update, phone_number_quality_update,
   phone_number_name_update, account_update, account_review_update, account_alerts, business_capability_update,
   security, smb_message_echoes, smb_app_state_sync, history`.
4. **Facebook Login for Business → Configurations → Create**: WhatsApp Embedded Signup, permissions
   `whatsapp_business_management`, `whatsapp_business_messaging`. Copy the **Configuration ID** into Platform
   settings. Add your domain to *Allowed domains for the JavaScript SDK* and *Valid OAuth redirect URIs*.
5. Business settings → **System users** → add an admin system user, assign your WABA and the app with full
   control, **Generate token** (never expires) with `whatsapp_business_management` + `whatsapp_business_messaging`.
   Admin panel → **WhatsApp → Our WABAs → Add** with the WABA ID and this token. Numbers are imported as platform
   senders; assign them to partners.
6. Click **Test connection**. Choose billing mode (Tech Provider unless you are an approved Solution Partner with a
   credit line).
7. Before onboarding real partners: request **Advanced Access / App Review** for `whatsapp_business_management`
   and `whatsapp_business_messaging` (screen-record: Embedded Signup, sending a template, creating a template),
   complete **Business Verification**, then switch the app to **Live**.

## 7. Razorpay configuration
1. Dashboard → **Account & Settings → API Keys**: generate Key ID + Secret (test mode first).
2. **Webhooks → Add**: URL `https://wa.example.com/api/webhooks/payments/razorpay`, secret = random string,
   events `payment.captured`, `payment.failed`, `order.paid`.
3. **Payment capture**: automatic.
4. Admin panel → **Settings → Payment gateway**: active gateway = Razorpay, Key ID, Key secret, Webhook secret,
   min/max amount → **Test**. Make a test-mode UPI payment (UPI ID `success@razorpay`) and check the wallet is
   credited exactly once.

## 8. Backups & monitoring
- Nightly 02:15 UTC: `backup-db.sh` → `/var/backups/cbwa` (DB dump + uploads, 14 days). Set `OFFSITE_TARGET`
  in the cron line for an off-site rsync copy. Test a restore monthly: `sudo /opt/cbwa/scripts/restore-db.sh <file>`.
- Every 5 min: `healthcheck.sh` restarts the service after two failed health checks (set `ALERT_EMAIL`).
- Logs: app JSON logs rotate daily (30 days, 5 GB cap); `/etc/logrotate.d/cbwa` rotates script logs.
- TLS renewal: `systemctl list-timers certbot.timer`.

## 9. Security checklist
- [ ] `ufw status` shows only 22, 80, 443; MySQL bound to 127.0.0.1
- [ ] `/etc/cbwa/*.env|*.cnf` readable only by root/cbwa; encryption key backed up
- [ ] `CBWA_SWAGGER_ENABLED=false` (or Nginx allow-list for /swagger-ui)
- [ ] Seed admin password changed; admin 2FA enabled (Settings → Security)
- [ ] SSH: key-only login, `PermitRootLogin no`
- [ ] Meta app secret & Razorpay secrets entered only in the admin panel (stored encrypted)
