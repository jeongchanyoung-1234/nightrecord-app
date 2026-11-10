#!/bin/bash
# Docker Compose로 앱을 올리고, Cursor와 무관하게 재시작합니다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo ".env 파일이 필요합니다." >&2
  exit 1
fi

# 포트 점유 중인 로컬 python 서버가 있으면 종료
if command -v fuser >/dev/null 2>&1; then
  fuser -k 8080/tcp >/dev/null 2>&1 || true
fi
pkill -f '/home/jcy1996/nightrecord/server/app.py' >/dev/null 2>&1 || true
pkill -f 'python3 server/app.py' >/dev/null 2>&1 || true
sleep 1

# 시스템 서비스가 떠 있으면 Docker와 충돌하므로 중지 시도(권한 있을 때만)
if systemctl is-active --quiet nightrecord.service 2>/dev/null; then
  systemctl stop nightrecord.service 2>/dev/null || true
fi

docker pull python:3.12-slim
docker compose up -d --remove-orphans
docker compose ps
echo
echo "건강 확인:"
sleep 1
curl -s -o /dev/null -w "GET / -> %{http_code}\n" http://127.0.0.1:8080/ || true
echo "로그: docker compose -f $ROOT/docker-compose.yml logs -f --tail=100"
