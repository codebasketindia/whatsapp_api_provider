#!/usr/bin/env bash
# Restore a backup made by backup-db.sh.  Usage: restore-db.sh /var/backups/cbwa/db-cbwa-YYYYmmdd-HHMMSS.sql.gz
# Stops the app, restores into the cbwa database, starts the app again.
set -euo pipefail
FILE="${1:?Usage: restore-db.sh <db-backup.sql.gz>}"
read -r -p "This REPLACES all data in database 'cbwa'. Type RESTORE to continue: " ok
[[ "$ok" == "RESTORE" ]] || { echo "Cancelled"; exit 1; }
systemctl stop cbwa
gunzip -c "$FILE" | mysql --defaults-extra-file=/etc/cbwa/restore.cnf cbwa
systemctl start cbwa
echo "Restored $FILE"
