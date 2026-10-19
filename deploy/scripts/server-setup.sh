#!/usr/bin/env bash
# One-time Ubuntu 22.04/24.04 server preparation (run as root). No Docker.
#   sudo DOMAIN=wa.example.com EMAIL=you@example.com ./deploy/scripts/server-setup.sh
set -euo pipefail
: "${DOMAIN:?Set DOMAIN=your.domain}"
: "${EMAIL:?Set EMAIL for Let's Encrypt}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

apt-get update
apt-get install -y openjdk-21-jre-headless mysql-server nginx certbot python3-certbot-nginx curl rsync unzip ufw
# Build tools (only needed if you build on the server)
apt-get install -y maven
if ! command -v node >/dev/null; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi

# Firewall: SSH + HTTP(S) only. MySQL and 8080 stay on localhost.
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable

# Service user and directories
id cbwa >/dev/null 2>&1 || useradd --system --home /opt/cbwa --shell /usr/sbin/nologin cbwa
install -d -o cbwa -g cbwa -m 750 /opt/cbwa /opt/cbwa/scripts /var/lib/cbwa /var/lib/cbwa/storage /var/log/cbwa
install -d -o root -g cbwa -m 750 /etc/cbwa
install -d -m 700 /var/backups/cbwa
install -d -o www-data -g www-data /var/www/cbwa /var/www/certbot

# MySQL
install -m 644 "$ROOT/deploy/mysql/cbwa.cnf" /etc/mysql/mysql.conf.d/cbwa.cnf
systemctl restart mysql
mysql < "$ROOT/deploy/mysql/setup.sql"

# App config template
if [[ ! -f /etc/cbwa/cbwa.env ]]; then
  install -o root -g cbwa -m 640 "$ROOT/.env.example" /etc/cbwa/cbwa.env
  sed -i "s#https://wa.example.com#https://$DOMAIN#g" /etc/cbwa/cbwa.env
  sed -i "s#^CBWA_JWT_SECRET=.*#CBWA_JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')#" /etc/cbwa/cbwa.env
  sed -i "s#^CBWA_ENCRYPTION_KEY=.*#CBWA_ENCRYPTION_KEY=$(openssl rand -base64 32)#" /etc/cbwa/cbwa.env
  sed -i "s#^CBWA_API_SECRET_PEPPER=.*#CBWA_API_SECRET_PEPPER=$(openssl rand -base64 32)#" /etc/cbwa/cbwa.env
  echo ">>> Edit /etc/cbwa/cbwa.env: set CBWA_DB_PASSWORD, CBWA_SEED_ADMIN_EMAIL/PASSWORD. BACK UP CBWA_ENCRYPTION_KEY."
fi

# systemd, logrotate
install -m 644 "$ROOT/deploy/systemd/cbwa.service" /etc/systemd/system/cbwa.service
install -m 644 "$ROOT/deploy/logrotate/cbwa" /etc/logrotate.d/cbwa
systemctl daemon-reload
systemctl enable cbwa

# Nginx + TLS
sed "s/wa.example.com/$DOMAIN/g" "$ROOT/deploy/nginx/cbwa.conf" > /etc/nginx/sites-available/cbwa.conf
ln -sf /etc/nginx/sites-available/cbwa.conf /etc/nginx/sites-enabled/cbwa.conf
rm -f /etc/nginx/sites-enabled/default
# Get the certificate with a temporary HTTP-only config first
cat > /etc/nginx/sites-available/cbwa-bootstrap.conf <<NGX
server { listen 80; server_name $DOMAIN; location /.well-known/acme-challenge/ { root /var/www/certbot; } }
NGX
ln -sf /etc/nginx/sites-available/cbwa-bootstrap.conf /etc/nginx/sites-enabled/cbwa.conf
nginx -t && systemctl reload nginx
certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" --email "$EMAIL" --agree-tos --non-interactive
ln -sf /etc/nginx/sites-available/cbwa.conf /etc/nginx/sites-enabled/cbwa.conf
nginx -t && systemctl reload nginx
systemctl enable --now certbot.timer

# Cron jobs
cat > /etc/cron.d/cbwa <<CRON
15 2 * * * root /opt/cbwa/scripts/backup-db.sh >> /var/log/cbwa/backup.log 2>&1
*/5 * * * * root /opt/cbwa/scripts/healthcheck.sh
CRON
echo ">>> Server ready. Next: edit /etc/cbwa/cbwa.env, create /etc/cbwa/backup.cnf, then run deploy/scripts/deploy.sh"
