#!/usr/bin/env bash
# Raw HTTP hardening checks against a running backend (real Tomcat, no MockMvc): content types, chunked
# bodies, HEAD/OPTIONS/TRACE, CORS, firewall (traversal, ;, //), oversized URL/header/upload, actuator and
# API docs exposure, security headers, webhook signatures. Prints PASS/FAIL per check, exits non-zero on failure.
#   BASE=http://localhost:8080 ADMIN_MOBILE=9876543210 ADMIN_PASSWORD='...' ALLOWED_ORIGIN=https://wa.example.com \
#     deploy/e2e/raw-http-checks.sh
# The admin must already have changed the temporary password. Run against the backend port, not through Nginx.
B="${BASE:-http://localhost:8080}"
ADMIN_MOBILE="${ADMIN_MOBILE:?set ADMIN_MOBILE}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?set ADMIN_PASSWORD}"
ALLOWED_ORIGIN="${ALLOWED_ORIGIN:-https://app.example.com}"
TMP="$(mktemp -d)"
fails=0
check() { # name expected actual
  if [[ "$3" == $2 ]]; then echo "PASS $1 ($3)"; else echo "FAIL $1 expected=$2 actual=$3"; fails=$((fails+1)); fi
}
code() { curl -s -o $TMP/body -w "%{http_code}" "$@"; }

TOKEN=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"mobile\":\"$ADMIN_MOBILE\",\"password\":\"$ADMIN_PASSWORD\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
check "login works (prod)" "200" "$(code -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"mobile\":\"$ADMIN_MOBILE\",\"password\":\"$ADMIN_PASSWORD\"}")"
check "text/plain body to JSON endpoint -> 4xx" "4??" "$(code -X POST $B/api/auth/login -H 'Content-Type: text/plain' -d 'hello')"
grep -q '"success":false' $TMP/body && echo "PASS text/plain envelope" || { echo "FAIL text/plain envelope: $(cat $TMP/body | head -c 200)"; fails=$((fails+1)); }
check "no content-type" "4??" "$(code -X POST $B/api/auth/login --data-binary '{"mobile":"1"}' -H 'Content-Type:')"
check "chunked body" "401" "$(code -X POST $B/api/auth/login -H 'Content-Type: application/json' -H 'Transfer-Encoding: chunked' -d '{"mobile":"$ADMIN_MOBILE","password":"wrong"}')"
check "xml body" "4??" "$(code -X POST $B/api/auth/login -H 'Content-Type: application/xml' -d '<a/>')"
check "HEAD on health" "200" "$(curl -s -o /dev/null -w '%{http_code}' -I $B/actuator/health)"
check "HEAD on protected" "401" "$(curl -s -o /dev/null -w '%{http_code}' -I $B/api/admin/roles)"
check "method not allowed" "4??" "$(code -X PATCH $B/api/auth/login -H 'Content-Type: application/json' -d '{}')"
grep -q '"success":false' $TMP/body && echo "PASS 405 envelope" || { echo "FAIL 405 envelope: $(head -c 200 $TMP/body)"; fails=$((fails+1)); }
check "unknown api route (authed)" "404" "$(code -H "Authorization: Bearer $TOKEN" $B/api/admin/does-not-exist)"
grep -q '"success":false' $TMP/body && echo "PASS 404 envelope" || { echo "FAIL 404 envelope: $(head -c 200 $TMP/body)"; fails=$((fails+1)); }
check "non-api route denied" "4??" "$(code $B/etc/passwd)"
check "encoded traversal" "400" "$(code "$B/api/files/..%2F..%2Fetc%2Fpasswd" -H "Authorization: Bearer $TOKEN")"
check "double slash" "4??" "$(code "$B/api//admin/roles" -H "Authorization: Bearer $TOKEN")"
check "semicolon matrix param" "400" "$(code "$B/api/admin/roles;jsessionid=x" -H "Authorization: Bearer $TOKEN")"
check "very long url" "4??" "$(code "$B/api/admin/partners?search=$(python3 -c 'print("a"*20000)')" -H "Authorization: Bearer $TOKEN")"
check "huge header" "4??" "$(code $B/api/me -H "Authorization: Bearer $TOKEN" -H "X-Junk: $(python3 -c 'print("a"*70000)')")"
check "CORS preflight foreign origin" "403" "$(code -X OPTIONS $B/api/admin/roles -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: GET')"
check "CORS preflight allowed origin" "200" "$(code -X OPTIONS $B/api/admin/roles -H "Origin: $ALLOWED_ORIGIN" -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: authorization')"
check "CORS foreign origin real GET has no ACAO" "" "$(curl -s -D - -o /dev/null $B/api/public/branding -H 'Origin: https://evil.example' | grep -i '^access-control-allow-origin' | tr -d '\r')"
check "public API preflight any origin" "200" "$(code -X OPTIONS $B/api/v1/public/wallet/balance -H 'Origin: https://partner.example' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: x-api-key,x-api-secret')"
check "swagger disabled in prod" "4??" "$(code $B/v3/api-docs)"
check "swagger ui disabled in prod" "4??" "$(code $B/swagger-ui.html)"
check "actuator env not exposed" "4??" "$(code $B/actuator/env)"
check "actuator beans not exposed" "4??" "$(code $B/actuator/beans)"
check "health has no details" "" "$(curl -s $B/actuator/health | grep -o 'components\|diskSpace\|db')"
HDRS=$(curl -s -D - -o /dev/null $B/api/public/branding)
echo "$HDRS" | grep -qi '^x-content-type-options: nosniff' && echo "PASS nosniff header" || { echo "FAIL nosniff header"; fails=$((fails+1)); }
echo "$HDRS" | grep -qi '^x-frame-options: DENY' && echo "PASS frame-options header" || { echo "FAIL frame-options header"; fails=$((fails+1)); }
echo "$HDRS" | grep -qi '^x-request-id:' && echo "PASS request-id header" || { echo "FAIL request-id header"; fails=$((fails+1)); }
check "server error page hides stack" "" "$(curl -s "$B/api/admin/partners?page=abc" -H "Authorization: Bearer $TOKEN" | grep -o 'Exception\|at in\.codebasket\|trace')"
check "malformed JSON from prod" "400" "$(code -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"mobile":')"
check "deeply nested JSON" "4??" "$(code -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "$(python3 -c 'print("["*5000 + "]"*5000)')")"
check "unicode NUL in json" "4??" "$(code -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"mobile":"98765\u0000","password":"x"}')"
check "webhook GET bad token" "403" "$(code "$B/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=x&hub.challenge=1")"
check "webhook POST no signature" "401" "$(code -X POST $B/api/webhooks/whatsapp -H 'Content-Type: application/json' -d '{}')"
check "webhook POST text/plain no sig" "401" "$(code -X POST $B/api/webhooks/whatsapp -H 'Content-Type: text/plain' -d 'x')"
check "razorpay webhook no sig" "400" "$(code -X POST $B/api/webhooks/payments/razorpay -H 'Content-Type: application/json' -d '{}')"
check "razorpay webhook text/plain" "4??" "$(code -X POST $B/api/webhooks/payments/razorpay -H 'Content-Type: text/plain' -d 'x')"
check "multipart to JSON endpoint" "4??" "$(code -X POST $B/api/auth/login -F 'mobile=1')"
check "upload over servlet limit" "4??" "$(head -c 21000000 /dev/zero > $TMP/big.bin; code -X POST $B/api/admin/settings/company/logo -H "Authorization: Bearer $TOKEN" -F "file=@$TMP/big.bin;type=image/png")"
grep -q '"success":false' $TMP/body && echo "PASS big upload envelope" || { echo "FAIL big upload envelope: $(head -c 200 $TMP/body)"; fails=$((fails+1)); }
check "TRACE method" "4??" "$(code -X TRACE $B/api/me)"
echo "FAILS=$fails"
rm -rf "$TMP"
[[ $fails -eq 0 ]]
