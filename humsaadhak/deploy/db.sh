#!/usr/bin/env bash
# HumSaadhak 2.0 — the PostgreSQL database on its own in Docker, for development: run the backend from your IDE or
# `mvn spring-boot:run` against it, look at data with psql, or load the old system's dump. (The whole stack, database
# included, is deploy/deploy.sh up.) Windows PowerShell: deploy/db.ps1.
#
#   deploy/db.sh start                 create the container the first time, start it, wait until ready, show how to connect
#   eval "$(deploy/db.sh env)"         set DB_URL, DB_USER, DB_PASSWORD … for the backend and DATABASE_URL for search
#   deploy/db.sh help                  every command
#
# Settings: DB_NAME, DB_USER, DB_PASSWORD from deploy/.env (with the filled file: saadhak_demo, postgres, admin).
# Without deploy/.env: humsaadhak / humsaadhak and a random password kept in deploy/.env.db. Environment variables win.
# Options from the environment:
#   DB_PORT_LOCAL=5432   port on this computer (bound to 127.0.0.1 only: nothing outside this computer can connect)
#   DB_TLS=1             TLS with the internal certificates (deploy/tls/out, made if missing); plain connections refused
#   ASSUME_YES=1         answer yes to confirmations;   DRY_RUN=1   print docker commands instead of running them
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$DEPLOY_DIR")"
ENV_FILE="$DEPLOY_DIR/.env"
DEV_ENV_FILE="$DEPLOY_DIR/.env.db"
CONTAINER=humsaadhak-db-dev
VOLUME=humsaadhak-db-dev-data
IMAGE=pgvector/pgvector:pg16
PORT="${DB_PORT_LOCAL:-5432}"
TLS="${DB_TLS:-0}"
DRY_RUN="${DRY_RUN:-0}"
ASSUME_YES="${ASSUME_YES:-0}"

if [[ -t 1 ]]; then B=$'\e[1m'; G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; N=$'\e[0m'; else B='' G='' Y='' R='' N=''; fi
info() { printf '%s==>%s %s\n' "$B" "$N" "$*" >&2; }
ok()   { printf '  %sok%s   %s\n' "$G" "$N" "$*" >&2; }
warn() { printf '  %swarn%s %s\n' "$Y" "$N" "$*" >&2; }
die()  { printf '%sfailed:%s %s\n' "$R" "$N" "$*" >&2; exit 1; }
run()  { if [[ "$DRY_RUN" == 1 ]]; then printf '  [dry-run] %s\n' "$*" >&2; else "$@"; fi; }
confirm() {
  [[ "$ASSUME_YES" == 1 ]] && return 0
  local answer; read -r -p "$1 Type $2 to continue: " answer; [[ "$answer" == "$2" ]] || die "cancelled"
}
need_docker() {
  [[ "$DRY_RUN" == 1 ]] && return 0
  command -v docker >/dev/null 2>&1 || die "Docker is not installed (Docker Desktop on Windows and macOS, Docker Engine on Linux)"
  docker info >/dev/null 2>&1 || die "Docker is not running, or this user may not use it"
}
file_get() { [[ -f "$1" ]] || return 0; sed -n "s/^$2=//p" "$1" | tail -n 1; }

# ---------------------------------------------------------------- settings: environment > deploy/.env > deploy/.env.db
DB_NAME="${DB_NAME:-$(file_get "$ENV_FILE" DB_NAME)}"
DB_USER="${DB_USER:-$(file_get "$ENV_FILE" DB_USER)}"
DB_PASSWORD="${DB_PASSWORD:-$(file_get "$ENV_FILE" DB_PASSWORD)}"
if [[ -z "$DB_PASSWORD" ]]; then
  DB_NAME="${DB_NAME:-$(file_get "$DEV_ENV_FILE" DB_NAME)}"; DB_USER="${DB_USER:-$(file_get "$DEV_ENV_FILE" DB_USER)}"
  DB_PASSWORD="$(file_get "$DEV_ENV_FILE" DB_PASSWORD)"
fi
DB_NAME="${DB_NAME:-humsaadhak}"; DB_USER="${DB_USER:-humsaadhak}"
[[ "$DB_NAME" =~ ^[a-z_][a-z0-9_]*$ ]] || die "DB_NAME may contain only lowercase letters, digits and _"
[[ "$DB_USER" =~ ^[a-z_][a-z0-9_]*$ ]] || die "DB_USER may contain only lowercase letters, digits and _"

ensure_password() {   # without deploy/.env: a random password, kept so the next start uses the same one
  [[ -n "$DB_PASSWORD" ]] && return 0
  DB_PASSWORD="$(openssl rand -hex 16 2>/dev/null || head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  printf '# Local development database (deploy/db.sh). Never commit.\nDB_NAME=%s\nDB_USER=%s\nDB_PASSWORD=%s\n' \
    "$DB_NAME" "$DB_USER" "$DB_PASSWORD" > "$DEV_ENV_FILE"
  chmod 600 "$DEV_ENV_FILE"
  ok "no deploy/.env: made a password and kept it in deploy/.env.db"
}

exists()  { [[ "$DRY_RUN" != 1 ]] && docker container inspect "$CONTAINER" >/dev/null 2>&1; }
running() { [[ "$DRY_RUN" != 1 ]] && [[ "$(docker container inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" == true ]]; }
config_id() { echo "port=$PORT tls=$TLS image=$IMAGE"; }
in_db() { run docker exec -i "$CONTAINER" "$@"; }   # psql, pg_dump … inside the container (local socket, no password)

# ---------------------------------------------------------------- start
create_container() {
  local args=(run -d --name "$CONTAINER" --restart unless-stopped
    --label "humsaadhak.db.config=$(config_id)" --label "humsaadhak.db.user=$DB_USER"
    -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASSWORD"
    -p "127.0.0.1:$PORT:5432" -v "$VOLUME:/var/lib/postgresql/data"
    --health-cmd "pg_isready -U $DB_USER -d postgres" --health-interval 3s --health-timeout 3s --health-retries 40)
  if [[ "$TLS" == 1 ]]; then
    [[ -f "$DEPLOY_DIR/tls/out/db/tls.key" ]] || { info "Making the internal certificates (deploy/tls/out)"; run sh "$DEPLOY_DIR/tls/make-certs.sh"; }
    # The key must belong to the postgres user inside the container: copied there at start, never changed on this computer.
    args+=(-v "$DEPLOY_DIR/tls/out/db:/tls-src:ro" -v "$DEPLOY_DIR/postgres/pg_hba.conf:/etc/postgresql/pg_hba.conf:ro"
      --entrypoint bash "$IMAGE" -c 'mkdir -p /tls && install -o postgres -g postgres -m 600 /tls-src/tls.key /tls/tls.key && install -o postgres -g postgres -m 644 /tls-src/tls.crt /tls/tls.crt && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tls/tls.crt -c ssl_key_file=/tls/tls.key -c ssl_min_protocol_version=TLSv1.2 -c hba_file=/etc/postgresql/pg_hba.conf')
  else
    args+=("$IMAGE")
  fi
  run docker "${args[@]}" > /dev/null
}

wait_ready() {
  [[ "$DRY_RUN" == 1 ]] && return 0
  local i status
  for (( i = 0; i < 60; i++ )); do
    status=$(docker container inspect -f '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo missing)
    [[ "$status" == healthy ]] && return 0
    [[ "$status" == missing ]] && die "the container stopped: deploy/db.sh logs"
    sleep 2
  done
  docker logs --tail 30 "$CONTAINER" >&2; die "the database did not become ready"
}

cmd_start() {
  need_docker; ensure_password
  if exists; then
    local cfg user
    cfg=$(docker container inspect -f '{{index .Config.Labels "humsaadhak.db.config"}}' "$CONTAINER")
    user=$(docker container inspect -f '{{index .Config.Labels "humsaadhak.db.user"}}' "$CONTAINER")
    [[ -z "$user" || "$user" == "$DB_USER" ]] || die "the data was created for user $user, but DB_USER is now $DB_USER: keep $user, or delete the data with deploy/db.sh reset"
    if [[ "$cfg" != "$(config_id)" ]]; then
      info "Settings changed ($cfg → $(config_id)): recreating the container, keeping the data"
      run docker rm -f "$CONTAINER" > /dev/null; create_container
    elif ! running; then info "Starting $CONTAINER"; run docker start "$CONTAINER" > /dev/null
    else ok "$CONTAINER is already running"; fi
  else
    info "Creating $CONTAINER ($IMAGE, data in volume $VOLUME)"
    create_container
  fi
  wait_ready
  if [[ "$DRY_RUN" != 1 ]]; then
    docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -Atc 'select 1' >/dev/null 2>&1 ||
      die "user $DB_USER cannot connect: the data volume was made with other settings (deploy/db.sh reset starts afresh)"
    if [[ "$(docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -Atc "select 1 from pg_database where datname = '$DB_NAME'")" != 1 ]]; then
      docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -qc "create database \"$DB_NAME\""; ok "created database $DB_NAME"
    fi
    docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -qc 'create extension if not exists vector' >/dev/null
  fi
  ok "PostgreSQL 16 with pgvector is ready on localhost:$PORT$([[ "$TLS" == 1 ]] && echo ' (TLS only)')"
  cat >&2 <<EOF

  Database   $DB_NAME   user $DB_USER   (password in $([[ -f "$ENV_FILE" && -n "$(file_get "$ENV_FILE" DB_PASSWORD)" ]] && echo deploy/.env || echo deploy/.env.db))
  JDBC URL   $(jdbc_url)
  psql       deploy/db.sh psql
  Backend    eval "\$(deploy/db.sh env)" && mvn -f backend spring-boot:run      (needs Amazon Corretto 17 and Maven)
EOF
}

jdbc_url() {
  local url="jdbc:postgresql://localhost:$PORT/$DB_NAME"
  [[ "$TLS" == 1 ]] && url+="?sslmode=verify-full&sslrootcert=$DEPLOY_DIR/tls/out/ca.crt"
  echo "$url"
}

# ---------------------------------------------------------------- env: settings for running the backend and search locally
cmd_env() {
  ensure_password
  local q="'" k v
  printf "export DB_URL=%s\nexport DB_USER=%s\nexport DB_PASSWORD=%s\n" "$q$(jdbc_url)$q" "$q$DB_USER$q" "$q$DB_PASSWORD$q"
  local pg="postgresql://$DB_USER:$DB_PASSWORD@localhost:$PORT/$DB_NAME"
  [[ "$TLS" == 1 ]] && pg+="?sslmode=verify-full&sslrootcert=$DEPLOY_DIR/tls/out/ca.crt"
  printf "export DATABASE_URL=%s\n" "$q$pg$q"
  # The rest of the backend's settings, so it starts as configured in deploy/.env
  for k in HUMSAADHAK_FIELD_KEY SEARCH_TOKEN HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL MAIL_PROVIDER MAIL_FROM MAIL_REDIRECT_TO MAIL_LOG_LINKS \
           GRAPH_TENANT_ID GRAPH_CLIENT_ID GRAPH_CLIENT_SECRET; do
    v=$(file_get "$ENV_FILE" "$k"); [[ -n "$v" ]] && printf "export %s=%s\n" "$k" "$q$v$q"
  done
  printf "export APP_URL='http://localhost:8080'\n"
}

# ---------------------------------------------------------------- the rest
cmd_stop() { need_docker; exists || { ok "not created yet"; return 0; }; info "Stopping $CONTAINER (data kept)"; run docker stop "$CONTAINER" > /dev/null; ok "stopped"; }

cmd_status() {
  need_docker
  if ! exists; then echo "  $CONTAINER: not created (deploy/db.sh start)"; return 0; fi
  docker ps -a --filter "name=^${CONTAINER}$" --format '  {{.Names}}: {{.Status}}, {{.Ports}}'
  if running; then
    echo "  database $DB_NAME: $(docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -Atc "select pg_size_pretty(pg_database_size(current_database()))" 2>/dev/null || echo '?'), $(docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -Atc "select count(*) from information_schema.tables where table_schema not in ('pg_catalog','information_schema')" 2>/dev/null || echo '?') tables"
  fi
}

cmd_logs() { need_docker; if [[ "${1:-}" == -f ]]; then run docker logs --tail 100 -f "$CONTAINER"; else run docker logs --tail 100 "$CONTAINER"; fi; }

cmd_psql() { need_docker; running || [[ "$DRY_RUN" == 1 ]] || die "not running: deploy/db.sh start"
  if [[ -t 0 ]]; then run docker exec -it "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" "$@"
  else in_db psql -U "$DB_USER" -d "$DB_NAME" "$@"; fi; }

cmd_dump() {
  need_docker; running || [[ "$DRY_RUN" == 1 ]] || die "not running: deploy/db.sh start"
  local out=${1:-$REPO_DIR/backups/dev-$DB_NAME-$(date +%Y%m%d-%H%M%S).dump}
  run mkdir -p "$(dirname "$out")"
  info "Dumping $DB_NAME to $out"
  if [[ "$DRY_RUN" == 1 ]]; then in_db pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc; return 0; fi
  in_db pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$out"; chmod 600 "$out"; ok "$(du -h "$out" | cut -f1) written"
}

load_into() {   # load_into <database> <dump file>: custom-format dumps with pg_restore, plain SQL (.sql/.sql.gz) with psql
  local db=$1 file=$2
  local reader=(cat "$file") magic
  [[ "$file" == *.gz ]] && reader=(gzip -dc "$file")
  magic=$( { "${reader[@]}" | head -c 5; } 2>/dev/null || true)
  if [[ "$DRY_RUN" == 1 ]]; then in_db pg_restore -U "$DB_USER" -d "$db" --no-owner --no-privileges; return 0; fi
  if [[ "$magic" == PGDMP ]]; then "${reader[@]}" | in_db pg_restore -U "$DB_USER" -d "$db" --no-owner --no-privileges
  else "${reader[@]}" | in_db psql -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 -q > /dev/null; fi
}

cmd_restore() {
  local file=${1:-}; [[ -n "$file" ]] || die "usage: deploy/db.sh restore <dump file>"
  [[ "$DRY_RUN" == 1 || -f "$file" ]] || die "$file does not exist"
  need_docker; running || [[ "$DRY_RUN" == 1 ]] || die "not running: deploy/db.sh start"
  confirm "Replace everything in database $DB_NAME with $file?" RESTORE
  info "Restoring $file into $DB_NAME"
  in_db psql -U "$DB_USER" -d postgres -qc "drop database if exists \"$DB_NAME\" with (force)"
  in_db psql -U "$DB_USER" -d postgres -qc "create database \"$DB_NAME\""
  load_into "$DB_NAME" "$file"
  ok "restored"
}

# The old system's dump → schema "legacy" of this database, ready for one run of the app with the legacy-migration profile.
cmd_load_legacy() {
  local file=${1:-}; [[ -n "$file" ]] || die "usage: deploy/db.sh load-legacy <old database dump>"
  [[ "$DRY_RUN" == 1 || -f "$file" ]] || die "$file does not exist"
  need_docker; running || [[ "$DRY_RUN" == 1 ]] || die "not running: deploy/db.sh start"
  info "Loading the old system's dump into schema legacy of $DB_NAME"
  in_db psql -U "$DB_USER" -d postgres -qc 'drop database if exists legacy_import with (force)'
  in_db psql -U "$DB_USER" -d postgres -qc 'create database legacy_import'
  load_into legacy_import "$file"
  in_db psql -U "$DB_USER" -d legacy_import -qc 'drop schema if exists legacy cascade' -c 'alter schema public rename to legacy'
  in_db psql -U "$DB_USER" -d "$DB_NAME" -qc 'drop schema if exists legacy cascade'
  in_db sh -c "pg_dump -U '$DB_USER' -d legacy_import -n legacy --no-owner --no-privileges | psql -U '$DB_USER' -d '$DB_NAME' -v ON_ERROR_STOP=1 -q"
  in_db psql -U "$DB_USER" -d postgres -qc 'drop database legacy_import'
  ok "the old data is in schema legacy"
  cat >&2 <<EOF
  Next, run the backend once with the migration profile (Flyway V100 copies the records, then the old files are copied):
    eval "\$(deploy/db.sh env)" && SPRING_PROFILES_ACTIVE=legacy-migration LEGACY_UPLOAD_DIR=/path/to/old/uploads mvn -f backend spring-boot:run
  Then check: deploy/db.sh psql -c 'select * from legacy_migration_report'
EOF
}

cmd_reset() {
  need_docker
  confirm "Delete $CONTAINER and ALL its data (volume $VOLUME)?" reset
  run docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
  run docker volume rm "$VOLUME" > /dev/null 2>&1 || true
  ok "deleted; deploy/db.sh start creates an empty database"
}

usage() {
  cat <<EOF
HumSaadhak 2.0 — the PostgreSQL 16 + pgvector database alone in Docker, for development (container $CONTAINER).
The whole stack, database included: deploy/deploy.sh up. On Windows PowerShell: deploy/db.ps1.

  start                    create (first time) and start; waits until ready; prints how to connect
  stop                     stop (data kept)
  status                   running?, port, size, number of tables
  env                      settings to run the backend and search locally: eval "\$(deploy/db.sh env)"
  psql [args]              open psql in the database (e.g. deploy/db.sh psql -c 'select count(*) from purpose')
  logs [-f]                the database's log
  dump [file]              save the database (custom format) to backups/dev-<name>-<date>.dump or [file]
  restore <file>           replace the database with a dump (asks you to type RESTORE)
  load-legacy <dump>       put the old system's dump into schema legacy, for one run of the legacy migration
  reset                    delete the container and all its data (asks you to type reset)

Database $DB_NAME, user $DB_USER, port $PORT on 127.0.0.1$([[ "$TLS" == 1 ]] && echo ', TLS only'). Change the port with DB_PORT_LOCAL=5433;
DB_TLS=1 serves TLS with the internal certificates (the app then uses sslmode=verify-full).
EOF
}

main() {
  local cmd=${1:-start}; shift || true
  case "$cmd" in
    start|up) cmd_start ;;
    stop|down) cmd_stop ;;
    status) cmd_status ;;
    env) cmd_env ;;
    psql) cmd_psql "$@" ;;
    logs) cmd_logs "${1:-}" ;;
    dump) cmd_dump "${1:-}" ;;
    restore) cmd_restore "${1:-}" ;;
    load-legacy) cmd_load_legacy "${1:-}" ;;
    reset) cmd_reset ;;
    help|-h|--help) usage ;;
    *) usage; die "unknown command: $cmd" ;;
  esac
}
main "$@"
