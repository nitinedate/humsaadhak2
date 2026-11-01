#!/bin/sh
# pgAdmin start (deploy/compose.yaml): registers the HumSaadhak database for the pgAdmin user, then hands over to
# pgAdmin's own entrypoint. In server mode pgAdmin opens certificate files inside the user's storage folder, so the
# database's CA certificate is copied there and the connection names it "/db-ca.crt" (sslmode verify-full).
set -e
DATA="${PGADMIN_DATA_DIR:-/var/lib/pgadmin}"

# The storage folder name, derived exactly as pgAdmin does (web/pgadmin/utils/paths.py, preprocess_username)
user="$PGADMIN_DEFAULT_EMAIL"
case "$user" in ''|[0-9]*) user="pga_user_$user" ;; esac
user=$(printf '%s' "$user" | sed -e 's/@/_/g' -e 's#/#slash#g' -e 's#\\#slash#g')
mkdir -p "$DATA/storage/$user"
cp /humsaadhak/db-ca.crt "$DATA/storage/$user/db-ca.crt"     # refreshed at every start (e.g. after renew-certs)

# Imported by pgAdmin on its first start (it keeps the server, and any password you save, afterwards)
cat > /tmp/humsaadhak-servers.json <<JSON
{"Servers": {"1": {
  "Name": "HumSaadhak ($HS_DB_NAME)", "Group": "HumSaadhak",
  "Host": "$HS_DB_HOST", "Port": $HS_DB_PORT, "MaintenanceDB": "$HS_DB_NAME", "Username": "$HS_DB_USER",
  "ConnectionParameters": {"sslmode": "verify-full", "sslrootcert": "/db-ca.crt", "connect_timeout": 10}
}}}
JSON
export PGADMIN_SERVER_JSON_FILE=/tmp/humsaadhak-servers.json
exec "${PGADMIN_ENTRYPOINT:-/entrypoint.sh}"
