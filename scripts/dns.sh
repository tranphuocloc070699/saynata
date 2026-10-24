#!/usr/bin/env bash
# Creates or updates the DNS records of saynata.com in Cloudflare (DNS only, grey cloud), so Caddy on the VPS can get
# the HTTPS certificates itself. Safe to run again: an existing record is updated, never duplicated.
# Needs CLOUDFLARE_API_TOKEN (zone saynata.com, "Edit zone DNS") in scripts/.env, which git ignores. Local only:
# the server never needs this token. Guide: doc-11, section "Domain saynata.com".
#   scripts/dns.sh            # show what would change
#   scripts/dns.sh --apply    # change it
set -euo pipefail

ZONE="${DNS_ZONE:-saynata.com}"
SERVER_IP="${SERVER_IP:-110.172.29.29}"
NAMES=("$ZONE" "www.$ZONE" "api.$ZONE" "admin.$ZONE")

cd "$(dirname "$0")"
[ -f .env ] && { set -a; . ./.env; set +a; }
: "${CLOUDFLARE_API_TOKEN:?Put CLOUDFLARE_API_TOKEN in scripts/.env (see doc-11)}"
apply=false; [ "${1:-}" = "--apply" ] && apply=true

api() { curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" "$@"; }
base="https://api.cloudflare.com/client/v4"
zone_id=$(api "$base/zones?name=$ZONE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["result"][0]["id"])')

for name in "${NAMES[@]}"; do
  found=$(api "$base/zones/$zone_id/dns_records?type=A&name=$name" | python3 -c '
import json,sys; r=json.load(sys.stdin)["result"]
print(" ".join(str(r[0][k]) for k in ("id", "content", "proxied")) if r else "")')
  body="{\"type\":\"A\",\"name\":\"$name\",\"content\":\"$SERVER_IP\",\"ttl\":1,\"proxied\":false}"
  if [ -z "$found" ]; then
    echo "add     $name -> $SERVER_IP (DNS only)"
    $apply && api -X POST "$base/zones/$zone_id/dns_records" -d "$body" >/dev/null
  else
    read -r id content proxied <<<"$found"
    if [ "$content" = "$SERVER_IP" ] && [ "$proxied" = "False" ]; then
      echo "ok      $name"
    else
      echo "update  $name: $content proxied=$proxied -> $SERVER_IP DNS only"
      $apply && api -X PUT "$base/zones/$zone_id/dns_records/$id" -d "$body" >/dev/null
    fi
  fi
done
$apply || echo "Dry run. Add --apply to change Cloudflare."
