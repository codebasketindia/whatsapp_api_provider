#!/usr/bin/env bash
# Build and deploy a new version on the VPS (run from the repository root as a sudo user).
#   ./deploy/scripts/deploy.sh
# Steps: build JAR + React, back up DB, install files, restart service, wait for health.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WEB_ROOT="${WEB_ROOT:-/var/www/cbwa}"
APP_DIR="${APP_DIR:-/opt/cbwa}"

echo "==> Building backend"
(cd "$ROOT/backend" && mvn -q -B -DskipTests clean package)

echo "==> Building frontend"
(cd "$ROOT/frontend" && npm ci --no-audit --no-fund && npm run build)

echo "==> Backing up database before upgrade"
sudo /opt/cbwa/scripts/backup-db.sh || echo "WARNING: backup failed - continuing"

echo "==> Installing backend"
sudo install -o cbwa -g cbwa -m 640 "$ROOT/backend/target/cbwa.jar" "$APP_DIR/cbwa.jar.new"
sudo mv "$APP_DIR/cbwa.jar.new" "$APP_DIR/cbwa.jar"
sudo install -o root -g root -m 750 "$ROOT"/deploy/scripts/*.sh "$APP_DIR/scripts/"

echo "==> Installing frontend"
sudo rsync -a --delete "$ROOT/frontend/dist/" "$WEB_ROOT/"
sudo chown -R www-data:www-data "$WEB_ROOT"

echo "==> Restarting service"
sudo systemctl restart cbwa
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"UP"'; then
    echo "==> Healthy"; exit 0
  fi
  sleep 2
done
echo "ERROR: application did not become healthy - check: journalctl -u cbwa -n 200"
exit 1
