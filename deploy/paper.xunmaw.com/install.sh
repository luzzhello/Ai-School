#!/bin/bash
# Run on server: bash /opt/paper/install.sh
set -euo pipefail

PAPER_DIR=/opt/paper
NGINX_CONF=/etc/nginx/start.conf
MYSQL="mysql -S /tmp/mysql.sock -uroot -p123QWER."

echo "==> Create directories..."
mkdir -p "$PAPER_DIR"/{web/dist,backend,upload,logs,ssl}
mkdir -p /etc/nginx/cert

echo "==> Install SSL certs..."
if [ -f "$PAPER_DIR/ssl/paper.xunmaw.com.pem" ]; then
  cp "$PAPER_DIR/ssl/paper.xunmaw.com.pem" /etc/nginx/cert/
  cp "$PAPER_DIR/ssl/paper.xunmaw.com.key" /etc/nginx/cert/
  chmod 600 /etc/nginx/cert/paper.xunmaw.com.key
fi

echo "==> Init database ai_sc (skip create, apply updates only)..."
if [ -f "$PAPER_DIR/init-sql/02-updates.sql" ]; then
  echo "    Apply updates to ai_sc..."
  $MYSQL ai_sc < "$PAPER_DIR/init-sql/02-updates.sql" || true
fi

echo "==> Configure nginx..."
if [ ! -f /etc/nginx/config/paper_xunmaw.conf ]; then
  cp "$PAPER_DIR/paper.xunmaw.com.conf" /etc/nginx/config/paper_xunmaw.conf
  echo "    Installed /etc/nginx/config/paper_xunmaw.conf"
else
  echo "    Nginx already configured, skip"
fi
nginx -t && nginx -s reload

echo "==> Configure systemd..."
cp "$PAPER_DIR/paper-backend.service" /etc/systemd/system/paper-backend.service
systemctl daemon-reload
systemctl enable paper-backend
systemctl restart paper-backend

sleep 8
if systemctl is-active --quiet paper-backend; then
  echo "==> Backend started"
  curl -sf http://127.0.0.1:6039/actuator/health | head -c 200 || echo "health pending"
else
  echo "==> Backend failed, see $PAPER_DIR/logs/backend.log"
  tail -80 "$PAPER_DIR/logs/backend.log" || true
  exit 1
fi

echo "==> Done: https://paper.xunmaw.com"
