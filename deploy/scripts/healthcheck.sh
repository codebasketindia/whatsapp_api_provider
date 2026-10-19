#!/usr/bin/env bash
# Cron every 5 min: restarts the service if /actuator/health is not UP twice in a row and
# optionally alerts an email address (needs `mail` from mailutils).
#   */5 * * * * /opt/cbwa/scripts/healthcheck.sh
set -uo pipefail
STATE=/var/lib/cbwa/.health_failures
URL=http://127.0.0.1:8080/actuator/health
if curl -fsS --max-time 10 "$URL" | grep -q '"UP"'; then
  echo 0 > "$STATE"; exit 0
fi
n=$(( $(cat "$STATE" 2>/dev/null || echo 0) + 1 ))
echo "$n" > "$STATE"
if (( n >= 2 )); then
  systemctl restart cbwa
  echo 0 > "$STATE"
  if [[ -n "${ALERT_EMAIL:-}" ]] && command -v mail >/dev/null; then
    echo "cbwa was restarted by healthcheck at $(date -u)" | mail -s "[cbwa] service restarted" "$ALERT_EMAIL"
  fi
fi
