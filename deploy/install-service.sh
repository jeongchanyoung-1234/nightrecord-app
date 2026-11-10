#!/bin/bash
# 시스템 systemd로 앱을 등록합니다. (Docker 대신 네이티브 실행)
# 사용: sudo bash deploy/install-service.sh
set -euo pipefail
if [[ "${EUID}" -ne 0 ]]; then
  echo "root 권한이 필요합니다. sudo bash deploy/install-service.sh" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UNIT_SRC="$ROOT/deploy/nightrecord.service"
UNIT_DST=/etc/systemd/system/nightrecord.service

# Docker로 떠 있으면 충돌 방지를 위해 중지
if command -v docker >/dev/null 2>&1; then
  (cd "$ROOT" && docker compose stop nightrecord >/dev/null 2>&1) || true
fi

install -m 644 "$UNIT_SRC" "$UNIT_DST"
systemctl daemon-reload
systemctl enable --now nightrecord.service
systemctl --no-pager --full status nightrecord.service || true
echo "nightrecord.service 활성: http://127.0.0.1:8080"
