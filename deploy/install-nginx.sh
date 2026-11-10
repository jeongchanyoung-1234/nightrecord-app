#!/bin/bash
set -euo pipefail
if [[ "${EUID}" -ne 0 ]]; then
  echo "nginx 설정은 root 권한이 필요합니다. sudo bash deploy/install-nginx.sh" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
install -d -m 755 /var/www/html
install -m 644 "$ROOT/deploy/nightrecord.nginx.conf" /etc/nginx/sites-available/nightrecord
ln -sfn /etc/nginx/sites-available/nightrecord /etc/nginx/sites-enabled/nightrecord

# 인증서 발급 전에도 nginx -t가 통과하도록, 없으면 임시 self-signed 사용
if [[ ! -f /etc/letsencrypt/live/nightrecord.app/fullchain.pem ]]; then
  tmpconf="$(mktemp)"
  sed -e 's|/etc/letsencrypt/live/nightrecord.app/fullchain.pem|/etc/letsencrypt/live/nightrecord.duckdns.org/fullchain.pem|' \
      -e 's|/etc/letsencrypt/live/nightrecord.app/privkey.pem|/etc/letsencrypt/live/nightrecord.duckdns.org/privkey.pem|' \
      "$ROOT/deploy/nightrecord.nginx.conf" > "$tmpconf"
  install -m 644 "$tmpconf" /etc/nginx/sites-available/nightrecord
  rm -f "$tmpconf"
fi

nginx -t
systemctl reload nginx

# DNS가 있는 이름만 인증서에 포함 (없으면 LE가 NXDOMAIN으로 실패함)
DOMAINS=(-d nightrecord.app)
if dig +short www.nightrecord.app A | grep -q . || dig +short www.nightrecord.app AAAA | grep -q .; then
  DOMAINS+=(-d www.nightrecord.app)
fi
if dig +short dev.nightrecord.app A | grep -q . || dig +short dev.nightrecord.app AAAA | grep -q .; then
  DOMAINS+=(-d dev.nightrecord.app)
fi

certbot certonly --webroot -w /var/www/html \
  "${DOMAINS[@]}" \
  --expand \
  --non-interactive --agree-tos --register-unsafely-without-email \
  --keep-until-expiring

install -m 644 "$ROOT/deploy/nightrecord.nginx.conf" /etc/nginx/sites-available/nightrecord
nginx -t
systemctl reload nginx
echo "https://nightrecord.app"
echo "https://dev.nightrecord.app/admin.html"
