#!/usr/bin/env bash
# Nightly MySQL backup + uploaded files backup with 14-day retention.
# Cron (root):  15 2 * * * /opt/cbwa/scripts/backup-db.sh >> /var/log/cbwa/backup.log 2>&1
# Credentials: /etc/cbwa/backup.cnf  ([client] user=cbwa_backup password=...)  chmod 600
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/cbwa}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
DB_NAME="${DB_NAME:-cbwa}"
STORAGE_DIR="${STORAGE_DIR:-/var/lib/cbwa/storage}"
STAMP="$(date -u +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

echo "[$(date -u +%FT%TZ)] Dumping database $DB_NAME"
mysqldump --defaults-extra-file=/etc/cbwa/backup.cnf --single-transaction --quick --routines --triggers \
  --set-gtid-purged=OFF "$DB_NAME" | gzip -9 > "$BACKUP_DIR/db-$DB_NAME-$STAMP.sql.gz"

echo "[$(date -u +%FT%TZ)] Archiving uploads"
tar -C "$(dirname "$STORAGE_DIR")" -czf "$BACKUP_DIR/storage-$STAMP.tar.gz" "$(basename "$STORAGE_DIR")"

# Integrity check of the dump
gzip -t "$BACKUP_DIR/db-$DB_NAME-$STAMP.sql.gz"

echo "[$(date -u +%FT%TZ)] Removing backups older than $RETENTION_DAYS days"
find "$BACKUP_DIR" -type f \( -name 'db-*.sql.gz' -o -name 'storage-*.tar.gz' \) -mtime +"$RETENTION_DAYS" -delete

# Optional off-site copy: set OFFSITE_TARGET (e.g. user@backup-host:/backups/cbwa) and SSH keys.
if [[ -n "${OFFSITE_TARGET:-}" ]]; then
  rsync -az "$BACKUP_DIR/" "$OFFSITE_TARGET/"
fi
echo "[$(date -u +%FT%TZ)] Backup finished"
