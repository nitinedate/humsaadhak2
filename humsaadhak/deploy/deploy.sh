#!/usr/bin/env bash
# HumSaadhak 2.0 — one script for the whole life of a single-server deployment (Docker Compose).
#
#   deploy/deploy.sh init            create deploy/.env with random secrets (asks for the domain names)
#   deploy/deploy.sh up              check, make certificates, build, start, wait until healthy, smoke-test
#   deploy/deploy.sh --prod up       the same with deploy/.env.prod (production; e.g. a managed database)
#   deploy/deploy.sh help            every command
#
# Layout it expects (as in humsaadhak-2.0-all-projects.zip):   <folder>/humsaadhak/deploy/deploy.sh
#                                                               <folder>/humsaadhak-search/
# Settings come from deploy/.env (see deploy/.env.example), or deploy/.env.prod with --prod (see deploy/.env.prod.example),
# or any file with --env-file <path>. Script options, from the environment:
#   BACKUP_DIR=…        where backups go (default <folder>/humsaadhak/backups)
#   BACKUP_KEEP_DAYS=14 backups older than this are removed after each new backup
#   BACKUP_UPLOAD_CMD=… shell command run after each backup; the backup folder is $1, e.g.
#                       'rclone copy "$1" offsite:humsaadhak/$(basename "$1")'
#   HEALTH_TIMEOUT=900  seconds to wait for the app to become healthy
#   ASSUME_YES=1        answer yes to confirmations (for cron and CI)
#   DRY_RUN=1           print docker commands instead of running them
# shellcheck disable=SC2015  # ok, warn and info always succeed, so "A && ok || warn" is a safe if-else in this script
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$DEPLOY_DIR")"
SEARCH_DIR="$(dirname "$REPO_DIR")/humsaadhak-search"
ENV_FILE="$DEPLOY_DIR/.env"
ENV_EXAMPLE="$DEPLOY_DIR/.env.example"
ENV_FLAG=''                       # repeated in the cron lines when --prod or --env-file is used
COMPOSE_FILE="$DEPLOY_DIR/compose.yaml"
TLS_OUT="$DEPLOY_DIR/tls/out"
BACKUP_ROOT="${BACKUP_DIR:-$REPO_DIR/backups}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-900}"
DRY_RUN="${DRY_RUN:-0}"
ASSUME_YES="${ASSUME_YES:-0}"
PROJECT=humsaadhak
HELPER_IMAGE=busybox:1.36.1
MIN_COMPOSE=2.20.0
SECRETS=(DB_PASSWORD SEARCH_TOKEN STORAGE_ACCESS_KEY STORAGE_SECRET_KEY STORAGE_KMS_KEY HUMSAADHAK_FIELD_KEY)
KEYS_32_BYTES=(STORAGE_KMS_KEY HUMSAADHAK_FIELD_KEY)

# ---------------------------------------------------------------- output and helpers
if [[ -t 1 ]]; then B=$'\e[1m'; G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; N=$'\e[0m'; else B='' G='' Y='' R='' N=''; fi
info() { printf '%s==>%s %s\n' "$B" "$N" "$*"; }
ok()   { printf '  %sok%s   %s\n' "$G" "$N" "$*"; }
warn() { printf '  %swarn%s %s\n' "$Y" "$N" "$*" >&2; }
die()  { printf '%sfailed:%s %s\n' "$R" "$N" "$*" >&2; exit 1; }
trap 'printf "%sfailed:%s line %s: %s\n" "$R" "$N" "$LINENO" "$BASH_COMMAND" >&2' ERR

run() { if [[ "$DRY_RUN" == 1 ]]; then printf '  [dry-run] %s\n' "$*" >&2; else "$@"; fi; }   # stderr: visible even inside a pipe
have_docker() { [[ "$DRY_RUN" == 1 ]] || command -v docker >/dev/null 2>&1 || die "Docker is not installed on this machine (DEPLOYMENT.md, step 2)"; }
# COMPOSE_PROFILES comes from the env file: local-db starts the local database container
compose() { have_docker; run env COMPOSE_PROFILES="$(env_get COMPOSE_PROFILES)" docker compose --project-name "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
# A container left renamed by an interrupted update (for example "593b37636000_humsaadhak-search-1"): Compose renames
# the old container while it replaces it, and a leftover blocks the next attempt ("The container name ... is already in
# use"). Only this project's containers with that temporary name are removed; they were being replaced anyway.
remove_stale_containers() {
  [[ "$DRY_RUN" == 1 ]] && return 0
  local id name
  { docker ps -a --filter "label=com.docker.compose.project=$PROJECT" --format '{{.ID}} {{.Names}}' 2>/dev/null || true; } |
    while read -r id name; do
      if [[ "$name" =~ ^[0-9a-f]{12}_ ]]; then
        warn "removing $name, left behind by an interrupted update (it would block the new container)"
        docker rm -f "$id" >/dev/null || warn "could not remove $name: remove it with docker rm -f $id"
      fi
    done
}
# Every start goes through here, so a leftover never blocks it
compose_up() { remove_stale_containers; compose up -d "$@"; }
# docker compose for commands whose output we read (still printed only in dry-run mode)
compose_out() { have_docker; if [[ "$DRY_RUN" == 1 ]]; then printf '  [dry-run] docker compose %s\n' "$*" >&2; else COMPOSE_PROFILES="$(env_get COMPOSE_PROFILES)" docker compose --project-name "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; fi; }
env_label() { echo "${ENV_FILE#"$REPO_DIR"/}"; }

# ---------------------------------------------------------------- the database: local container or managed PostgreSQL
db_host() { local v; v=$(env_get DB_HOST); echo "${v:-db}"; }
db_port() { local v; v=$(env_get DB_PORT); echo "${v:-5432}"; }
db_name() { local v; v=$(env_get DB_NAME); echo "${v:-humsaadhak}"; }
db_user() { local v; v=$(env_get DB_USER); echo "${v:-humsaadhak}"; }
db_admin_db() { local v; v=$(env_get DB_ADMIN_DB); echo "${v:-postgres}"; }   # where CREATE DATABASE runs (DigitalOcean: defaultdb)
external_db() { [[ "$(db_host)" != db ]]; }
db_ca_file() { local f; f=$(env_get DB_CA_FILE); f="${f:-./tls/out/ca.crt}"; if [[ "$f" == /* ]]; then echo "$f"; else echo "$DEPLOY_DIR/${f#./}"; fi; }
# Client tools for a managed database; their major version must be at least the server's (DB_TOOLS_IMAGE=postgres:17 for 17)
db_tool_image() { local v; v=$(env_get DB_TOOLS_IMAGE); echo "${v:-pgvector/pgvector:pg16}"; }
db_conn() { echo "host=$(db_host) port=$(db_port) dbname=$1 user=$(db_user) sslmode=${2:-verify-full} sslrootcert=/db-ca.crt"; }

# pgtool <program> <database> [args…]: psql, pg_dump or pg_restore against one database of the configured server.
# Local: inside the db container. Managed: a throw-away PostgreSQL 16 client container that verifies the provider's
# certificate (sslmode=verify-full against DB_CA_FILE); the password goes in through the environment, not the command line.
pgtool() {
  local prog=$1 database=$2; shift 2
  if ! external_db; then compose exec -T db "$prog" -U "$(db_user)" -d "$database" "$@"; return; fi
  have_docker
  if [[ "$DRY_RUN" == 1 ]]; then printf '  [dry-run] %s %s on %s:%s (sslmode=verify-full) %s\n' "$prog" "$database" "$(db_host)" "$(db_port)" "$*" >&2; return 0; fi
  PGPASSWORD="$(env_get DB_PASSWORD)" docker run --rm -i -e PGPASSWORD -v "$(db_ca_file):/db-ca.crt:ro" "$(db_tool_image)" \
    "$prog" -d "$(db_conn "$database")" "$@"
}
psql_db() { pgtool psql "${PSQL_DB:-$(db_name)}" -v ON_ERROR_STOP=1 "$@"; }
pg_list() { if external_db; then run docker run --rm -i "$(db_tool_image)" pg_restore -l; else compose exec -T db pg_restore -l; fi; }

# A managed database server is shared: create this application's own database on it if it is not there yet, and refuse
# a database that already holds the old system's tables (Flyway would build the new schema on top of them).
ensure_database() {
  external_db || return 0
  info "Database $(db_name) on $(db_host):$(db_port)"
  [[ "$DRY_RUN" == 1 ]] && { PSQL_DB="$(db_admin_db)" psql_db -At -c "select 1 from pg_database where datname = '$(db_name)'"; return 0; }
  local exists old
  exists=$(PSQL_DB="$(db_admin_db)" psql_db -At -c "select 1 from pg_database where datname = '$(db_name)'") ||
    die "cannot connect to $(db_host):$(db_port) as $(db_user). Check DB_* and DB_CA_FILE in $(env_label), and that this server is a trusted source of the database (DEPLOYMENT.md, Managed database)"
  if [[ "$exists" == 1 ]]; then ok "database $(db_name) exists"
  else PSQL_DB="$(db_admin_db)" psql_db -q -c "create database \"$(db_name)\""; ok "created database $(db_name)"; fi
  local server tools
  server=$(psql_db -At -c 'show server_version_num' | cut -c1-2)
  tools=$(docker run --rm "$(db_tool_image)" pg_dump --version | grep -oE '[0-9]+' | head -n 1)
  (( tools >= server )) || die "the database runs PostgreSQL $server: set DB_TOOLS_IMAGE=postgres:$server in $(env_label) (backups need matching tools)"
  old=$(psql_db -At -c "select (to_regclass('public.benefactor') is not null and to_regclass('public.flyway_schema_history') is null)::int")
  [[ "$old" != 1 ]] || die "DB_NAME=$(db_name) is the old system's database. Use a new database (e.g. humsaadhak) and migrate the old one with migrate-legacy"
}

confirm() {   # confirm "question" [word to type]
  [[ "$ASSUME_YES" == 1 ]] && return 0
  local answer
  if [[ -n "${2:-}" ]]; then
    read -r -p "$1 Type $2 to continue: " answer; [[ "$answer" == "$2" ]] || die "cancelled"
  else
    read -r -p "$1 [y/N] " answer; [[ "$answer" =~ ^[Yy]$ ]] || die "cancelled"
  fi
}

env_get() { [[ -f "$ENV_FILE" ]] || return 0; sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1; }
env_set() {   # replaces KEY=… in place (values may contain = / +), or appends it
  local key=$1 value=$2 tmp
  if grep -q "^$key=" "$ENV_FILE"; then
    tmp=$(mktemp)
    awk -v k="$key" -v v="$value" 'index($0, k "=") == 1 { print k "=" v; next } { print }' "$ENV_FILE" > "$tmp"
    cat "$tmp" > "$ENV_FILE"; rm -f "$tmp"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}
has_profile() { [[ ",$(env_get COMPOSE_PROFILES)," == *",$1,"* ]]; }
pgadmin_port() { local v; v=$(env_get PGADMIN_PORT); echo "${v:-5050}"; }
is_local() { [[ "$1" == localhost || "$1" == *.localhost ]]; }
domain() { env_get DOMAIN; }
curl_opts() { if is_local "$(domain)"; then echo "--insecure"; fi; }   # localhost: Caddy's own CA, not in the host's trust store
version_ge() { [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n 1)" == "$2" ]]; }
need() { command -v "$1" >/dev/null 2>&1 || die "$1 is not installed. $2"; }

# ---------------------------------------------------------------- init: deploy/.env with random secrets
cmd_init() {
  need openssl "Install it with: sudo apt-get install -y openssl"
  if [[ -f "$ENV_FILE" ]]; then info "Keeping the existing $(env_label); filling in only what is empty"
  else cp "$ENV_EXAMPLE" "$ENV_FILE"; info "Created $(env_label) from $(basename "$ENV_EXAMPLE")"; fi
  chmod 600 "$ENV_FILE"

  local d f a e answer
  d="${DOMAIN:-$(env_get DOMAIN)}"; f="${FILES_DOMAIN:-$(env_get FILES_DOMAIN)}"
  a="${ACME_EMAIL:-$(env_get ACME_EMAIL)}"; e="${HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL:-$(env_get HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL)}"
  if [[ -t 0 && "$ASSUME_YES" != 1 ]]; then
    read -r -p "Public domain of the site (e.g. humsaadhak.org, or localhost for a trial) [${d:-localhost}]: " answer; d="${answer:-${d:-localhost}}"
    local fd; if is_local "$d"; then fd="files.localhost"; else fd="files.$d"; fi
    read -r -p "Domain for document downloads [${f:-$fd}]: " answer; f="${answer:-${f:-$fd}}"
    if ! is_local "$d"; then read -r -p "Email for Let's Encrypt certificate notices [${a}]: " answer; a="${answer:-$a}"; fi
    read -r -p "Email of the first super admin [${e}]: " answer; e="${answer:-$e}"
  fi
  d="${d:-localhost}"; f="${f:-$(is_local "$d" && echo files.localhost || echo "files.$d")}"
  env_set DOMAIN "$d"; env_set FILES_DOMAIN "$f"; env_set ACME_EMAIL "$a"; env_set HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL "$e"

  # Hex for anything that goes into a URL (the database password is part of the search service's connection string)
  if ! external_db; then [[ -n "$(env_get DB_PASSWORD)" ]] || env_set DB_PASSWORD "$(openssl rand -hex 24)"
  elif [[ -z "$(env_get DB_PASSWORD)" ]]; then warn "DB_PASSWORD is empty: fill in the managed database's password in $(env_label)"; fi
  [[ -n "$(env_get SEARCH_TOKEN)" ]]       || env_set SEARCH_TOKEN "$(openssl rand -hex 32)"
  [[ -n "$(env_get STORAGE_ACCESS_KEY)" ]] || env_set STORAGE_ACCESS_KEY "hs$(openssl rand -hex 10)"
  [[ -n "$(env_get STORAGE_SECRET_KEY)" ]] || env_set STORAGE_SECRET_KEY "$(openssl rand -hex 32)"
  [[ -n "$(env_get STORAGE_KMS_KEY)" ]]    || env_set STORAGE_KMS_KEY "$(openssl rand -base64 32)"
  [[ -n "$(env_get HUMSAADHAK_FIELD_KEY)" ]] || env_set HUMSAADHAK_FIELD_KEY "$(openssl rand -base64 32)"
  if is_local "$d"; then
    [[ "$(env_get MAIL_PROVIDER)" == graph ]] || { env_set MAIL_PROVIDER log; env_set MAIL_LOG_LINKS true; }
  fi
  # pgAdmin's own sign-in (used only when COMPOSE_PROFILES contains pgadmin)
  [[ -n "$(env_get PGADMIN_EMAIL)" || -z "$e" ]] || env_set PGADMIN_EMAIL "$e"
  [[ -n "$(env_get PGADMIN_PASSWORD)" ]] || env_set PGADMIN_PASSWORD "$(openssl rand -hex 12)"
  ok "$(env_label) ready (mode 600): DOMAIN=$d FILES_DOMAIN=$f"
  warn "Copy $(env_label) to a password manager or offline store now. HUMSAADHAK_FIELD_KEY and STORAGE_KMS_KEY cannot be"
  warn "recovered: without them bank account numbers and document files are unreadable."
  if ! is_local "$d" && [[ "$(env_get MAIL_PROVIDER)" != graph ]]; then
    warn "Email is not set up yet: set MAIL_PROVIDER=graph and GRAPH_* in deploy/.env (DEPLOYMENT.md, step 4) before 'up',"
    warn "or the first super admin will not receive the set-password email."
  fi
  info "Next: deploy/deploy.sh ${ENV_FLAG:+$ENV_FLAG }up"
}

# ---------------------------------------------------------------- check: prerequisites and configuration
# MAIL_PROVIDER=smtp: everything the mail server needs, and what Microsoft 365 would refuse
check_smtp() {
  [[ "$(env_get MAIL_PROVIDER)" == smtp ]] || return 0
  local k missing=() pw from user
  for k in SMTP_HOST SMTP_USERNAME SMTP_PASSWORD; do [[ -n "$(env_get "$k")" ]] || missing+=("$k"); done
  (( ${#missing[@]} == 0 )) || die "MAIL_PROVIDER=smtp needs ${missing[*]} in $(env_label)"
  pw="$(env_get SMTP_PASSWORD)"
  if [[ "$pw" == *'$'* && ! ( "$pw" == \'* && "$pw" == *\' ) ]]; then
    die "SMTP_PASSWORD contains a \$, which Docker Compose would replace: write it in single quotes, SMTP_PASSWORD='...'"
  fi
  from="$(env_get MAIL_FROM)"; user="$(env_get SMTP_USERNAME)"
  [[ -z "$from" || "${from,,}" == "${user,,}" ]] || warn "MAIL_FROM ($from) is not SMTP_USERNAME ($user): Microsoft 365 refuses it unless $user may 'Send As' $from"
  [[ -z "$(env_get SMTP_SSL_TRUST)" ]] ||
    warn "SMTP_SSL_TRUST=$(env_get SMTP_SSL_TRUST): that mail server's certificate is not verified (encrypted, but a forged certificate would be accepted)"
  [[ "$(env_get SMTP_HOST)" != *office365.com ]] ||
    warn "Microsoft 365 turns off password sign-in for SMTP (SMTP AUTH Basic) by default at the end of December 2026: move to MAIL_PROVIDER=graph before then (DEPLOYMENT.md, step 4)"
  local port; port="$(env_get SMTP_PORT)"
  ok "email through $(env_get SMTP_HOST):${port:-587} as $user (STARTTLS required)"
}

# What a mail server's refusal means, in plain words (Microsoft 365 codes)
explain_smtp_error() {
  case "$1" in
    *5.7.139*) echo "password sign-in for SMTP is turned off for this mailbox or organisation, or blocked by security defaults or Conditional Access: turn on Authenticated SMTP for the mailbox (DEPLOYMENT.md, step 4), or use MAIL_PROVIDER=graph" ;;
    *5.7.3*|*5.7.57*|*"Authentication unsuccessful"*|*"Login denied"*|*535*) echo "the mail server refused the user name or password: check SMTP_USERNAME and SMTP_PASSWORD (try them at https://outlook.office.com). An account with multi-factor sign-in needs an app password, and Authenticated SMTP must be on for the mailbox" ;;
    *5.7.60*|*SendAsDenied*) echo "$(env_get SMTP_USERNAME) may not send as $(env_get MAIL_FROM): set MAIL_FROM to SMTP_USERNAME, or give it Send As permission" ;;
    *"timed out"*|*"Could not resolve"*|*"Failed to connect"*|*"Connection refused"*) echo "the mail server could not be reached: check SMTP_HOST and SMTP_PORT, and that this network allows outgoing connections on that port" ;;
    *) echo "the test email was not sent (the mail server's answer is above)" ;;
  esac
}

# smtp-test: sign in to the mail server with the settings file and send one test email, without the app
cmd_smtp_test() {
  [[ "$(env_get MAIL_PROVIDER)" == smtp ]] || die "MAIL_PROVIDER is not smtp in $(env_label)"
  check_smtp
  local host port user from to pw netrc out
  host="$(env_get SMTP_HOST)"; port="$(env_get SMTP_PORT)"; port="${port:-587}"; user="$(env_get SMTP_USERNAME)"
  from="$(env_get MAIL_FROM)"; from="${from:-$user}"; to="$(env_get HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL)"; to="${to:-$user}"
  pw="$(env_get SMTP_PASSWORD)"
  if [[ ${#pw} -ge 2 && "$pw" == \'*\' ]]; then pw="${pw:1:${#pw}-2}"; fi
  info "Signing in to $host:$port as $user (STARTTLS) and sending a test email to $to"
  # the password goes through a file only this user can read, not the command line (visible to others in ps)
  netrc="$(mktemp)"; chmod 600 "$netrc"
  printf 'machine %s login %s password %s\n' "$host" "$user" "$pw" > "$netrc"
  if out="$(printf 'From: %s\r\nTo: %s\r\nSubject: HumSaadhak: test email\r\n\r\nA test email from smtp-test: the mail settings work.\r\n' "$from" "$to" |
            curl -sS -v --max-time 30 --url "smtp://$host:$port" --ssl-reqd --netrc-file "$netrc" \
                 --mail-from "$from" --mail-rcpt "$to" --upload-file - 2>&1)"; then
    rm -f "$netrc"; ok "test email sent to $to: the mail settings work"
  else
    rm -f "$netrc"
    out="$(grep -E '^< [45][0-9][0-9]|^curl: ' <<<"$out" | tail -n 3)"
    echo "  the mail server said: $out"
    die "$(explain_smtp_error "$out")"
  fi
}

check_config() {
  [[ -f "$ENV_FILE" ]] || die "$(env_label) is missing. Run: deploy/deploy.sh ${ENV_FLAG:+$ENV_FLAG }init"
  local missing=() k v n
  for k in DOMAIN FILES_DOMAIN "${SECRETS[@]}"; do [[ -n "$(env_get "$k")" ]] || missing+=("$k"); done
  (( ${#missing[@]} == 0 )) || die "$(env_label) has no value for: ${missing[*]}. Run: deploy/deploy.sh ${ENV_FLAG:+$ENV_FLAG }init"
  [[ "$(db_name)" =~ ^[a-z_][a-z0-9_]*$ ]] || die "DB_NAME may contain only lowercase letters, digits and _"
  [[ "$(env_get DB_PASSWORD)" =~ ^[A-Za-z0-9._~-]+$ ]] || die "DB_PASSWORD may contain only letters, digits and . _ ~ - (it is part of a connection URL)"
  local profiles; profiles=",$(env_get COMPOSE_PROFILES),"
  if external_db; then
    [[ -f "$(db_ca_file)" ]] || die "DB_CA_FILE $(db_ca_file) is missing: download the CA certificate from the database provider (DEPLOYMENT.md, Managed database)"
    [[ "$profiles" != *,local-db,* ]] || die "COMPOSE_PROFILES contains local-db, but DB_HOST is $(db_host): remove local-db"
    ok "managed database $(db_user)@$(db_host):$(db_port)/$(db_name), certificate checked against $(env_get DB_CA_FILE)"
  else
    [[ "$profiles" == *,local-db,* ]] || die "the local database needs COMPOSE_PROFILES=local-db in $(env_label) (or set DB_HOST for a managed database)"
  fi
  for k in "${KEYS_32_BYTES[@]}"; do
    v="$(env_get "$k")"; n=$(printf '%s' "$v" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d ' ')
    [[ "$n" == 32 ]] || die "$k must be 32 random bytes in base64 (openssl rand -base64 32); it decodes to $n bytes"
  done
  [[ "$(stat -c %a "$ENV_FILE" 2>/dev/null || echo 600)" == 600 ]] || { chmod 600 "$ENV_FILE"; warn "$(env_label) was readable by others; now mode 600"; }
  if is_local "$(domain)"; then
    ok "configuration complete (trial on $(domain))"
  else
    [[ -n "$(env_get ACME_EMAIL)" ]] || warn "ACME_EMAIL is empty: you will not get Let's Encrypt expiry notices"
    [[ "$(env_get MAIL_PROVIDER)" == graph || "$(env_get MAIL_PROVIDER)" == smtp ]] || warn "MAIL_PROVIDER is neither graph nor smtp: no email will reach anyone"
    [[ "$(env_get MAIL_LOG_LINKS)" != true ]] || die "MAIL_LOG_LINKS=true writes sign-in links to the log: only for localhost trials"
    [[ -n "$(env_get MAIL_REDIRECT_TO)" ]] && warn "MAIL_REDIRECT_TO is set: every email goes to $(env_get MAIL_REDIRECT_TO) (testing only)"
    ok "configuration complete for $(domain) and $(env_get FILES_DOMAIN)"
  fi
  [[ -n "$(env_get HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL)" ]] || warn "HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL is empty: on an empty database nobody can sign in"
  check_smtp
  if has_profile pgadmin; then
    local pe; pe="$(env_get PGADMIN_EMAIL)"
    [[ "$pe" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ && ! "$pe" =~ \.(localhost|local|test|invalid|example)$ ]] ||
      die "PGADMIN_EMAIL must be a real email address (pgAdmin refuses '$pe'); run deploy/deploy.sh ${ENV_FLAG:+$ENV_FLAG }init"
    local pp; pp="$(env_get PGADMIN_PASSWORD)"
    (( ${#pp} >= 8 )) || die "PGADMIN_PASSWORD must have at least 8 characters; run deploy/deploy.sh ${ENV_FLAG:+$ENV_FLAG }init"
    ok "pgAdmin on 127.0.0.1:$(pgadmin_port) (only this computer; on a server, use an SSH tunnel)"
  fi
}

cmd_check() {
  info "Checking prerequisites"
  need openssl "Install it with: sudo apt-get install -y openssl"
  need curl "Install it with: sudo apt-get install -y curl"
  [[ -d "$SEARCH_DIR" ]] || die "The search service is missing: expected $SEARCH_DIR next to $REPO_DIR"
  ok "projects found: $REPO_DIR and $SEARCH_DIR"
  if [[ "$DRY_RUN" == 1 ]]; then warn "dry run: Docker checks skipped"; else
    need docker "Install Docker Engine: see DEPLOYMENT.md, step 2"
    docker info >/dev/null 2>&1 || die "Docker is installed but not reachable. Is the daemon running, and is this user in the docker group?"
    local v; v="$(docker compose version --short 2>/dev/null | sed 's/^v//')" || true
    [[ -n "$v" ]] || die "The Docker Compose plugin is missing: sudo apt-get install -y docker-compose-plugin"
    version_ge "$v" "$MIN_COMPOSE" || die "Docker Compose $v is too old; $MIN_COMPOSE or newer is needed"
    ok "Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null), Compose $v"
  fi
  local mem_gb disk_gb
  mem_gb=$(awk '/MemTotal/ { printf "%d", $2/1024/1024 }' /proc/meminfo 2>/dev/null || echo 0)
  disk_gb=$(df -Pk "$REPO_DIR" | awk 'NR==2 { printf "%d", $4/1024/1024 }')
  (( mem_gb >= 7 )) && ok "memory ${mem_gb} GB" || warn "memory ${mem_gb} GB: 8 GB or more is recommended (the search models need about 3 GB)"
  (( disk_gb >= 20 )) && ok "free disk ${disk_gb} GB" || warn "free disk ${disk_gb} GB: 20 GB or more is recommended (images, database, files, backups)"
  if command -v ss >/dev/null 2>&1 && [[ "$DRY_RUN" != 1 ]]; then
    local p; for p in 80 443; do
      if ss -ltnH "sport = :$p" | grep -q . && ! compose_out ps --status running --services 2>/dev/null | grep -qx proxy; then
        warn "port $p is already in use by another program; stop it (e.g. sudo systemctl stop nginx apache2)"
      fi
    done
  fi
  check_config
  if ! is_local "$(domain)"; then
    local d ip
    for d in "$(domain)" "$(env_get FILES_DOMAIN)"; do
      ip=$(getent ahosts "$d" 2>/dev/null | awk 'NR==1 { print $1 }' || true)   # getent fails for unknown names
      [[ -n "$ip" ]] && ok "$d resolves to $ip (it must point to this server)" || warn "$d does not resolve yet: add a DNS A record (DEPLOYMENT.md, step 3)"
    done
  fi
}

# ---------------------------------------------------------------- certificates for the internal TLS
cert_days_left() { local end; end=$(openssl x509 -enddate -noout -in "$1" 2>/dev/null | cut -d= -f2) || return 1
  echo $(( ( $(date -d "$end" +%s) - $(date +%s) ) / 86400 )); }

cmd_certs() {
  local force=${1:-} left
  if [[ "$force" != --force && -f "$TLS_OUT/app/tls.crt" ]]; then
    left=$(cert_days_left "$TLS_OUT/app/tls.crt" || echo 0)
    if (( left > 30 )); then ok "internal certificates valid for $left more days"; return 0; fi
    warn "internal certificates expire in $left days: renewing"
  fi
  info "Making internal certificates (deploy/tls/out; the CA is kept if it exists)"
  sh "$DEPLOY_DIR/tls/make-certs.sh"
  ok "internal certificates valid for $(cert_days_left "$TLS_OUT/app/tls.crt") days; keep deploy/tls/out/ca.key offline as well"
}

cmd_renew_certs() {
  cmd_certs --force
  info "Handing the new certificates to the services"
  compose_up --force-recreate tls-init
  compose restart db storage search app proxy
  wait_healthy
}

# ---------------------------------------------------------------- start, health, smoke tests
# A container that keeps crashing will never become healthy: stop waiting and show its log (Docker restarts it each time)
crash_loop() {
  local name service restarts
  while read -r name service; do
    [[ -n "$name" ]] || continue
    restarts=$(docker inspect -f '{{.RestartCount}}' "$name" 2>/dev/null || echo 0)
    if [[ "$restarts" =~ ^[0-9]+$ ]] && (( restarts >= 3 )); then echo "$service $restarts"; return 0; fi
  done < <(compose_out ps -a --format '{{.Name}} {{.Service}}' 2>/dev/null || true)
  return 1
}

wait_healthy() {
  [[ "$DRY_RUN" == 1 ]] && { warn "dry run: not waiting for health"; return 0; }
  local url start=$SECONDS body
  url="https://$(domain)/actuator/health/readiness"
  info "Waiting for $url (up to ${HEALTH_TIMEOUT}s; the first start also loads the search models)"
  while (( SECONDS - start < HEALTH_TIMEOUT )); do
    # shellcheck disable=SC2046
    body=$(curl -fsS --max-time 10 $(curl_opts) "$url" 2>/dev/null || true)
    if [[ "$body" == *'"UP"'* ]]; then ok "healthy after $(( SECONDS - start ))s"; return 0; fi
    local crash
    if crash=$(crash_loop); then
      compose_out logs --tail 30 "${crash%% *}" || true
      die "the ${crash%% *} container keeps crashing (restarted ${crash##* } times), so the site cannot start. Its log is above"
    fi
    sleep 10
    (( (SECONDS - start) % 60 < 10 )) && printf '  … %ss, still starting: %s\n' "$(( SECONDS - start ))" \
      "$(compose_out ps --format '{{.Service}}={{.State}}' 2>/dev/null | tr '\n' ' ')"
  done
  compose_out ps || true
  printf '\n%sLast app log lines:%s\n' "$B" "$N"; compose_out logs --tail 60 app || true
  die "the app did not become healthy within ${HEALTH_TIMEOUT}s"
}

cmd_smoke() {
  [[ "$DRY_RUN" == 1 ]] && { warn "dry run: smoke tests skipped"; return 0; }
  local d f fails=0 code headers
  d="$(domain)"; f="$(env_get FILES_DOMAIN)"
  info "Smoke tests"
  # shellcheck disable=SC2046
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 $(curl_opts) "https://$d/") || true
  [[ "$code" == 200 ]] && ok "https://$d/ serves the app" || { warn "https://$d/ answered $code"; fails=$((fails+1)); }
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "http://$d/") || true
  [[ "$code" =~ ^30[178]$ ]] && ok "http:// redirects to https:// ($code)" || { warn "http://$d/ answered $code, expected a redirect"; fails=$((fails+1)); }
  # shellcheck disable=SC2046
  headers=$(curl -sS -D - -o /dev/null --max-time 15 $(curl_opts) "https://$d/" 2>/dev/null || true)
  grep -qi '^strict-transport-security:' <<<"$headers" && ok "HSTS header present" || { warn "no Strict-Transport-Security header"; fails=$((fails+1)); }
  grep -qi '^content-security-policy:' <<<"$headers" && ok "Content-Security-Policy header present" || { warn "no Content-Security-Policy header"; fails=$((fails+1)); }
  # shellcheck disable=SC2046
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 $(curl_opts) "https://$f/minio/health/live") || true
  [[ "$code" == 200 ]] && ok "https://$f/ (document downloads) is reachable" || { warn "https://$f/minio/health/live answered $code"; fails=$((fails+1)); }
  # shellcheck disable=SC2046
  if curl -s -o /dev/null --max-time 10 --tlsv1.1 --tls-max 1.1 $(curl_opts) "https://$d/" 2>/dev/null; then
    warn "TLS 1.1 was accepted"; fails=$((fails+1)); else ok "TLS 1.1 not accepted (the proxy allows 1.2 and 1.3)"; fi
  local plain=1
  if external_db; then
    PGPASSWORD="$(env_get DB_PASSWORD)" docker run --rm -e PGPASSWORD "$(db_tool_image)" psql -d "$(db_conn "$(db_name)" disable)" -c 'select 1' >/dev/null 2>&1 || plain=0
  else
    compose_out exec -T db psql "host=localhost sslmode=disable user=$(db_user) dbname=$(db_name)" -c 'select 1' >/dev/null 2>&1 || plain=0
  fi
  if (( plain == 1 )); then
    warn "the database accepted an unencrypted connection"; fails=$((fails+1)); else ok "the database refuses unencrypted connections"; fi
  (( fails == 0 )) && ok "all smoke tests passed" || die "$fails smoke test(s) failed"
}

# The first build downloads about 3 GB, so a slow or dropped connection is likely somewhere: retry the build. The
# Dockerfiles keep finished downloads in build caches, so a retry only fetches what is still missing.
build_images() {
  local attempt
  for attempt in 1 2 3; do
    if compose build --pull; then ok "images built"; return 0; fi
    if (( attempt < 3 )); then
      warn "the build failed, most often a download that timed out: retrying ($((attempt + 1)) of 3); finished downloads are kept"
      sleep 15
    fi
  done
  die "the build failed 3 times. If the log shows a timeout, check the connection and run this again: finished downloads are kept"
}

cmd_up() {
  cmd_check
  cmd_certs
  info "Building images (the first build downloads about 3 GB: Maven, npm and the search models)"
  build_images
  ensure_database
  info "Starting HumSaadhak"
  compose_up --remove-orphans
  wait_healthy
  cmd_smoke
  printf '\n%sHumSaadhak is running at https://%s%s\n' "$B" "$(domain)" "$N"
  local admin; admin="$(env_get HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL)"
  if [[ -n "$admin" ]]; then
    if [[ "$(env_get MAIL_PROVIDER)" == log && "$(env_get MAIL_LOG_LINKS)" == true ]]; then
      echo "  First sign-in: deploy/deploy.sh${ENV_FLAG:+ $ENV_FLAG} admin-link prints $admin's set-password link."
    elif [[ -n "$(env_get MAIL_REDIRECT_TO)" ]]; then
      echo "  First sign-in: $admin's set-password email goes to $(env_get MAIL_REDIRECT_TO) (every email is redirected there)."
    else echo "  First sign-in: $admin receives a set-password email (on an empty database)."; fi
  fi
  has_profile pgadmin && cmd_pgadmin
  echo "  Next: schedule backups (deploy/deploy.sh cron) and read DEPLOYMENT.md, \"After go-live\"."
}

cmd_pgadmin() {
  has_profile pgadmin || die "pgAdmin is off: add pgadmin to COMPOSE_PROFILES in $(env_label) and run deploy/deploy.sh ${ENV_FLAG:+$ENV_FLAG }up"
  local port; port=$(pgadmin_port)
  echo "  pgAdmin: http://localhost:$port on this computer (bound to 127.0.0.1). From your own computer, keep a tunnel open:"
  echo "           ssh -N -L $port:127.0.0.1:$port $(id -un)@$(hostname -f 2>/dev/null || hostname)   then open http://localhost:$port"
  echo "           sign in as $(env_get PGADMIN_EMAIL) with PGADMIN_PASSWORD from $(env_label); the database password is DB_PASSWORD"
}

cmd_down() { info "Stopping HumSaadhak (data is kept)"; compose stop; }

cmd_destroy() {
  confirm "This deletes the database, all document files and the certificates Caddy obtained." "$PROJECT"
  compose down --volumes --remove-orphans
  ok "removed; deploy/.env, deploy/tls/out and backups were kept"
}

cmd_status() {
  info "Containers"; compose ps
  if [[ "$DRY_RUN" != 1 ]]; then
    info "Health"
    # shellcheck disable=SC2046
    curl -fsS --max-time 10 $(curl_opts) "https://$(domain)/actuator/health" 2>/dev/null && echo || warn "health endpoint not reachable"
    info "Certificates"
    [[ -f "$TLS_OUT/app/tls.crt" ]] && echo "  internal: $(cert_days_left "$TLS_OUT/app/tls.crt") days left (renew: deploy/deploy.sh renew-certs)"
    is_local "$(domain)" || echo "  public:   $(echo | openssl s_client -servername "$(domain)" -connect "$(domain):443" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2) (renewed by Caddy)"
    info "Latest backups"
    local latest line; latest=$(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '20*' 2>/dev/null | sort -r | head -n 3 || true)
    if [[ -n "$latest" ]]; then while IFS= read -r line; do printf '  %s\n' "$line"; done <<<"$latest"; else echo "  none yet"; fi
  fi
}

cmd_logs() {
  local svc='' follow='' arg
  for arg in "$@"; do if [[ "$arg" == -f ]]; then follow=1; else svc="$arg"; fi; done
  compose logs --tail 200 ${follow:+--follow} ${svc:+"$svc"}
}

cmd_admin_link() {
  [[ "$(env_get MAIL_LOG_LINKS)" == true ]] || die "links are only logged on trials (MAIL_LOG_LINKS=true); otherwise the admin gets an email"
  local link; link=$(compose_out logs --no-log-prefix app 2>/dev/null | grep -o 'Mail link for [^:]*: https://[^ ]*set-password[^ ]*' | tail -n 1 || true)
  [[ -n "$link" ]] || die "no set-password link in the log yet (is HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL set and the database new?)"
  echo "${link#Mail link for }"
}

# ---------------------------------------------------------------- backup and restore
cmd_backup() {
  check_config
  local dir; dir="$BACKUP_ROOT/$(date +%Y%m%d-%H%M%S)${1:+-$1}"
  info "Backing up to $dir"
  run mkdir -p "$dir"; run chmod 700 "$BACKUP_ROOT" "$dir"
  if [[ "$DRY_RUN" == 1 ]]; then
    pgtool pg_dump "$(db_name)" -Fc; printf '  [dry-run]   > %s/db.dump\n' "$dir"
  else
    pgtool pg_dump "$(db_name)" -Fc > "$dir/db.dump"
    pg_list < "$dir/db.dump" > /dev/null || die "the database dump could not be read back"
  fi
  ok "database"
  run docker run --rm -v "${PROJECT}_storage-data:/data:ro" -v "$dir:/backup" "$HELPER_IMAGE" tar czf /backup/files.tar.gz -C /data .
  ok "document files"
  run cp "$ENV_FILE" "$dir/env"
  run tar czf "$dir/tls.tar.gz" -C "$DEPLOY_DIR/tls" out
  ok "configuration and internal certificates (they contain the encryption keys: keep backups private)"
  if [[ "$DRY_RUN" != 1 ]]; then
    chmod 600 "$dir"/*
    { echo "created: $(date -Iseconds)"; echo "domain: $(domain)"
      echo "app image: $(docker image inspect --format '{{.Id}}' humsaadhak/app:latest 2>/dev/null || echo unknown)"
      echo "search image: $(docker image inspect --format '{{.Id}}' humsaadhak/search:latest 2>/dev/null || echo unknown)"
      echo "database: $(db_user)@$(db_host):$(db_port)/$(db_name)"
      echo "migrations: $(psql_db -At -c 'select max(version::int) from flyway_schema_history where success' 2>/dev/null || echo unknown)"
    } > "$dir/MANIFEST"
    (cd "$dir" && sha256sum db.dump files.tar.gz env tls.tar.gz > SHA256SUMS)
    ok "$(du -sh "$dir" | cut -f1) written, with checksums"
  fi
  # BACKUP_UPLOAD_CMD gets the backup folder as $1, e.g. 'rclone copy "$1" offsite:humsaadhak/$(basename "$1")'
  if [[ -n "${BACKUP_UPLOAD_CMD:-}" ]]; then info "Copying the backup off the server"; run sh -c "$BACKUP_UPLOAD_CMD" _ "$dir"; fi
  if [[ "$DRY_RUN" != 1 ]]; then
    find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$BACKUP_KEEP_DAYS" -print -exec rm -rf {} + | sed 's/^/  removed old backup /'
  fi
  LAST_BACKUP="$dir"
}

cmd_restore() {
  local dir=${1:-} with_env=${2:-}
  [[ -n "$dir" ]] || die "usage: deploy/deploy.sh restore <backup folder> [--with-env]"
  [[ "$DRY_RUN" == 1 || -f "$dir/db.dump" ]] || die "$dir has no db.dump"
  if [[ "$DRY_RUN" != 1 ]]; then (cd "$dir" && sha256sum --quiet -c SHA256SUMS) || die "checksums do not match: the backup is damaged"; fi
  if [[ "$with_env" == --with-env ]]; then cp "$dir/env" "$ENV_FILE"; chmod 600 "$ENV_FILE"; tar xzf "$dir/tls.tar.gz" -C "$DEPLOY_DIR/tls"; ok "deploy/.env and certificates restored from the backup"
  elif [[ -f "$dir/env" ]]; then
    local k; for k in "${KEYS_32_BYTES[@]}"; do
      [[ "$(sed -n "s/^$k=//p" "$dir/env")" == "$(env_get "$k")" ]] || die "$k in deploy/.env differs from the backup: the restored data would be unreadable. Re-run with --with-env"
    done
  fi
  confirm "Restore $dir? The current database and document files are replaced." RESTORE
  info "Stopping the app, search and proxy"
  compose stop proxy app search
  ensure_database
  info "Restoring the database"
  if [[ "$DRY_RUN" == 1 ]]; then pgtool pg_restore "$(db_name)" --clean --if-exists --no-owner --single-transaction; printf '  [dry-run]   < %s/db.dump\n' "$dir"
  else pgtool pg_restore "$(db_name)" --clean --if-exists --no-owner --single-transaction < "$dir/db.dump"; fi
  ok "database"
  info "Restoring document files"
  compose stop storage
  run docker run --rm -v "${PROJECT}_storage-data:/data" -v "$dir:/backup:ro" "$HELPER_IMAGE" \
    sh -c 'find /data -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar xzf /backup/files.tar.gz -C /data'
  ok "document files"
  compose_up
  wait_healthy
}

# ---------------------------------------------------------------- update and rollback
cmd_update() {
  local no_rollback=${1:-}
  cmd_check
  cmd_certs
  info "1/4 Backup before the update"
  cmd_backup pre-update
  [[ "$DRY_RUN" == 1 ]] || echo "$LAST_BACKUP" > "$BACKUP_ROOT/.last-update"
  info "2/4 Keeping the running images as :previous"
  run docker tag humsaadhak/app:latest humsaadhak/app:previous
  run docker tag humsaadhak/search:latest humsaadhak/search:previous
  info "3/4 Building the new release"
  build_images
  info "4/4 Starting it (Flyway applies new database migrations on start)"
  compose_up --remove-orphans
  if ( wait_healthy ); then cmd_smoke; ok "update finished; deploy/deploy.sh rollback returns to the previous release"
  elif [[ "$no_rollback" == --no-rollback ]]; then die "the new release is not healthy (automatic rollback was turned off)"
  else warn "the new release is not healthy: rolling back"; ASSUME_YES=1 cmd_rollback; die "update rolled back; see the app log above"; fi
}

cmd_rollback() {
  local backup; backup=$(cat "$BACKUP_ROOT/.last-update" 2>/dev/null || true)
  [[ "$DRY_RUN" == 1 || -n "$backup" ]] || die "no update to roll back (no $BACKUP_ROOT/.last-update)"
  [[ "$DRY_RUN" == 1 ]] || docker image inspect humsaadhak/app:previous >/dev/null 2>&1 || die "there is no previous release image"
  confirm "Return to the previous release and restore the database and files from ${backup:-the pre-update backup}?" ROLLBACK
  info "Putting back the previous images"
  run docker tag humsaadhak/app:previous humsaadhak/app:latest
  run docker tag humsaadhak/search:previous humsaadhak/search:latest
  # The new release may have migrated the database: restore the backup taken just before it.
  ASSUME_YES=1 cmd_restore "${backup:-<pre-update backup>}"
  ok "rolled back"
}

# ---------------------------------------------------------------- one-time migration from the old system
cmd_migrate_legacy() {
  local dump=${1:-} uploads=${2:-}
  [[ -n "$dump" && -n "$uploads" ]] || die "usage: deploy/deploy.sh migrate-legacy <old database dump> <old uploads folder>"
  [[ "$DRY_RUN" == 1 || ( -f "$dump" && -d "$uploads" ) ]] || die "need an existing dump file and uploads folder"
  uploads="$(cd "$uploads" 2>/dev/null && pwd || echo "$uploads")"
  check_config
  confirm "Migrate the old system into this deployment? Run it on a copy first (DEPLOYMENT.md, step 8)." MIGRATE
  info "1/6 Backup first"; ensure_database; cmd_backup pre-legacy
  info "2/6 Loading the old dump into a scratch database"
  PSQL_DB="$(db_admin_db)" psql_db -q -c 'drop database if exists legacy_import' -c 'create database legacy_import'
  local reader=(cat "$dump") magic=''; [[ "$dump" == *.gz ]] && reader=(gzip -dc "$dump")
  # pg_dump's custom format starts with PGDMP; anything else is plain SQL. (Read the first bytes without pipefail tripping.)
  magic=$( { "${reader[@]}" | head -c 5; } 2>/dev/null || true)
  if [[ "$DRY_RUN" == 1 ]]; then printf '  [dry-run] load %s into legacy_import (pg_restore for custom dumps, psql for SQL)\n' "$dump"
  elif [[ "$magic" == PGDMP ]]; then
    "${reader[@]}" | pgtool pg_restore legacy_import --no-owner --no-privileges
  else
    "${reader[@]}" | PSQL_DB=legacy_import psql_db -q > /dev/null
  fi
  info "3/6 Moving it into the schema \"legacy\" of the HumSaadhak database"
  PSQL_DB=legacy_import psql_db -q -c 'drop schema if exists legacy cascade' -c 'alter schema public rename to legacy'
  psql_db -q -c 'drop schema if exists legacy cascade'
  pgtool pg_dump legacy_import -n legacy --no-owner --no-privileges | psql_db -q
  PSQL_DB="$(db_admin_db)" psql_db -q -c 'drop database legacy_import'
  ok "old data is in schema legacy"
  info "4/6 Running the migration (Flyway V100, then copying the old files into storage)"
  compose stop app
  compose run -d --no-deps --name humsaadhak-legacy-migration -e SPRING_PROFILES_ACTIVE=tls,legacy-migration \
    -e LEGACY_UPLOAD_DIR=/legacy-uploads -v "$uploads:/legacy-uploads:ro" app
  if [[ "$DRY_RUN" != 1 ]]; then
    local start=$SECONDS line=''
    until line=$(docker logs humsaadhak-legacy-migration 2>&1 | grep -E 'Legacy file copy finished|APPLICATION FAILED|Migration .* failed' | tail -n 1) && [[ -n "$line" ]]; do
      (( SECONDS - start < HEALTH_TIMEOUT )) || { docker logs --tail 80 humsaadhak-legacy-migration; docker rm -f humsaadhak-legacy-migration; die "the migration did not finish in time"; }
      sleep 10
    done
    docker logs --tail 40 humsaadhak-legacy-migration
    docker rm -f humsaadhak-legacy-migration > /dev/null
    [[ "$line" == *'Legacy file copy finished'* ]] || die "the migration failed; restore with: deploy/deploy.sh restore $LAST_BACKUP"
    ok "$line"
  fi
  info "5/6 Starting the app normally"
  compose_up app; wait_healthy
  info "6/6 Reconciliation (old against new)"
  psql_db -c 'select * from legacy_migration_report'
  echo "  Check every count and total. Then let people know: those whose old password was not hashed use \"Forgot password\"."
}

# The old system's database (LEGACY_DB_* in the env file) → a custom-format dump for migrate-legacy. Read-only.
cmd_dump_legacy() {
  check_config
  local host port name user mode conn out net=()
  host=$(env_get LEGACY_DB_HOST); port=$(env_get LEGACY_DB_PORT); name=$(env_get LEGACY_DB_NAME); user=$(env_get LEGACY_DB_USER)
  mode=$(env_get LEGACY_DB_SSLMODE); mode=${mode:-verify-full}; port=${port:-5432}
  [[ -n "$host" && -n "$name" && -n "$user" ]] || die "set LEGACY_DB_HOST, LEGACY_DB_NAME and LEGACY_DB_USER in $(env_label)"
  [[ "$host" == localhost || "$host" == 127.0.0.1 ]] && net=(--network host)   # the old database runs on this computer
  conn="host=$host port=$port dbname=$name user=$user sslmode=$mode"
  [[ "$mode" == verify-* ]] && conn+=" sslrootcert=/db-ca.crt"
  out="$BACKUP_ROOT/legacy-$name-$(date +%Y%m%d-%H%M%S).dump"
  info "Dumping the old database $user@$host:$port/$name (read-only) to $out"
  run mkdir -p "$BACKUP_ROOT"; run chmod 700 "$BACKUP_ROOT"
  if [[ "$DRY_RUN" == 1 ]]; then printf '  [dry-run] %s pg_dump -Fc -d "%s" > %s\n' "$(db_tool_image)" "$conn" "$out"; return 0; fi
  have_docker
  PGPASSWORD="$(env_get LEGACY_DB_PASSWORD)" docker run --rm -e PGPASSWORD "${net[@]}" -v "$(db_ca_file):/db-ca.crt:ro" "$(db_tool_image)" \
    pg_dump -Fc --no-owner --no-privileges -d "$conn" > "$out" ||
    { rm -f "$out"; die "could not dump the old database: check LEGACY_DB_*, the trusted sources, and DB_TOOLS_IMAGE (server version)"; }
  chmod 600 "$out"
  docker run --rm -i "$(db_tool_image)" pg_restore -l < "$out" > /dev/null || die "the dump could not be read back"
  ok "$(du -h "$out" | cut -f1) written"
  info "Next: deploy/deploy.sh${ENV_FLAG:+ $ENV_FLAG} migrate-legacy $out <folder with the old uploads>"
}

# ---------------------------------------------------------------- scheduled jobs
cmd_cron() {
  local self; self="$(cd "$DEPLOY_DIR" && pwd)/deploy.sh${ENV_FLAG:+ $ENV_FLAG}"
  cat <<EOF
# Add with: sudo crontab -e   (as root, or a user in the docker group)
# Nightly backup at 02:30 (kept $BACKUP_KEEP_DAYS days; set BACKUP_UPLOAD_CMD to copy each one off the server)
30 2 * * * ASSUME_YES=1 $self backup >> /var/log/humsaadhak-backup.log 2>&1
# Weekly check of the internal certificates: renewed, and the services restarted, when less than 30 days are left
15 3 * * 0 ASSUME_YES=1 $self renew-certs-if-needed >> /var/log/humsaadhak-certs.log 2>&1
EOF
}

cmd_renew_certs_if_needed() {
  local left; left=$(cert_days_left "$TLS_OUT/app/tls.crt" || echo 0)
  (( left > 30 )) && ok "internal certificates valid for $left more days" || cmd_renew_certs
}

usage() {
  cat <<EOF
HumSaadhak 2.0 deployment (single server, Docker Compose). Full guide: DEPLOYMENT.md

Usage: deploy/deploy.sh [--prod | --env-file <file>] <command>
  (no option)              settings from deploy/.env      (local database container)
  --prod                   settings from deploy/.env.prod (production, e.g. a managed database)

First deployment
  init                     create deploy/.env with random secrets; asks for the domains and the first admin's email
  check                    prerequisites, configuration, DNS, ports, memory and disk
  up                       check, certificates, build, start, wait until healthy, smoke tests
  admin-link               trials only: the first super admin's set-password link from the log
  pgadmin                  how to open pgAdmin, the database console (profile pgadmin; 127.0.0.1 only, SSH tunnel)
  smtp-test                sign in to the mail server (MAIL_PROVIDER=smtp) and send one test email
  match-eval               measure how accurately the engine matches purposes to causes and projects
  match-train              teach the engine your reviewers' decisions (after 100+ approved matches)

Running it
  status                   containers, health, certificate expiry, latest backups
  logs [service] [-f]      last 200 log lines (proxy, app, search, db, storage); -f follows
  smoke                    the smoke tests on their own
  down                     stop everything (data is kept)
  destroy                  delete everything including data (asks you to type the project name)

Data
  backup                   database, document files, deploy/.env and certificates, with checksums
  restore <dir> [--with-env]  put a backup back (asks you to type RESTORE)
  dump-legacy              dump the old system's database (LEGACY_DB_* settings) into backups/, read-only
  migrate-legacy <dump> <uploads-dir>   one-time move from the old system (backup first; asks you to type MIGRATE)

Releases and maintenance
  update [--no-rollback]   backup, build the new release, start it; rolls back by itself if it is not healthy
  rollback                 return to the release before the last update, with its data
  certs [--force]          make the internal certificates (renewed when less than 30 days are left)
  renew-certs              new internal certificates and restart the services that use them
  cron                     the crontab lines for nightly backups and certificate checks
EOF
}

main() {
  while [[ "${1:-}" == --prod || "${1:-}" == --env-file ]]; do
    if [[ "$1" == --prod ]]; then ENV_FILE="$DEPLOY_DIR/.env.prod"; ENV_EXAMPLE="$DEPLOY_DIR/.env.prod.example"; ENV_FLAG=--prod; shift
    else [[ -n "${2:-}" ]] || die "--env-file needs a file"
         ENV_FILE="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"; ENV_FLAG="--env-file $ENV_FILE"; shift 2; fi
  done
  local cmd=${1:-help}; shift || true
  case "$cmd" in
    init) cmd_init ;;
    check) cmd_check ;;
    up|deploy) cmd_up ;;
    admin-link) cmd_admin_link ;;
    pgadmin) cmd_pgadmin ;;
    smtp-test) cmd_smtp_test ;;
    status) cmd_status ;;
    logs) cmd_logs "$@" ;;
    smoke) cmd_smoke ;;
    match-eval) info "Measuring matching accuracy with the real models (loading them takes a minute or two)"
                compose exec -T search python -m training.golden_eval ;;
    match-train) info "Fine-tuning the embedding model on approved matches (needs at least 100; can take a while)"
                 compose exec -T search python -m training.fine_tune /models/humsaadhak-embedding ;;
    down|stop) cmd_down ;;
    destroy) cmd_destroy ;;
    backup) cmd_backup ;;
    restore) cmd_restore "${1:-}" "${2:-}" ;;
    dump-legacy) cmd_dump_legacy ;;
    migrate-legacy) cmd_migrate_legacy "${1:-}" "${2:-}" ;;
    update) cmd_update "${1:-}" ;;
    rollback) cmd_rollback ;;
    certs) cmd_certs "${1:-}" ;;
    renew-certs) cmd_renew_certs ;;
    renew-certs-if-needed) cmd_renew_certs_if_needed ;;
    cron) cmd_cron ;;
    help|-h|--help) usage ;;
    *) usage; die "unknown command: $cmd" ;;
  esac
}
main "$@"
