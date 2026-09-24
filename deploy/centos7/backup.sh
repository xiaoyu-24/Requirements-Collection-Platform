#!/usr/bin/env bash
set -euo pipefail

: "${BACKUP_ROOT:=/www/backup/requirements-platform}"
: "${ATTACHMENTS_ROOT:=/www/wwwroot/requirements-platform/data/uploads}"
: "${MYSQL_CNF:=/etc/requirements-platform/mysql.cnf}"
: "${MYSQL_DATABASE:=requirements_platform}"

stamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="$BACKUP_ROOT/$stamp"
mkdir -p "$backup_dir"

mysqldump \
  --defaults-extra-file="$MYSQL_CNF" \
  --single-transaction \
  --routines \
  --events \
  --no-tablespaces \
  "$MYSQL_DATABASE" > "$backup_dir/requirements.sql"

tar -czf "$backup_dir/attachments.tar.gz" \
  -C "$(dirname "$ATTACHMENTS_ROOT")" "$(basename "$ATTACHMENTS_ROOT")"

{
  echo "batch=$stamp"
  echo "database=$MYSQL_DATABASE"
  echo "attachments=$ATTACHMENTS_ROOT"
} > "$backup_dir/manifest.txt"

echo "Backup completed: $backup_dir"
