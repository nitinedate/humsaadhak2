#!/usr/bin/env sh
# Internal certificates for encryption in transit between HumSaadhak's own services (app, search, db, storage).
# Browsers never see these: the public site gets its certificate from Let's Encrypt through Caddy (deploy/Caddyfile).
#
#   deploy/tls/make-certs.sh                # writes deploy/tls/out/
#   OUT=/secure/place DAYS=397 deploy/tls/make-certs.sh
#
# Keys are ECDSA P-256, certificates SHA-256, and every certificate names its service (subjectAltName), because every
# client verifies the hostname as well as the signature. Re-run before the leaf certificates expire (DAYS, default 397);
# the CA is kept and reused if it already exists, so clients keep trusting the new certificates.
set -eu
OUT="${OUT:-$(dirname "$0")/out}"
DAYS="${DAYS:-397}"
CA_DAYS="${CA_DAYS:-1825}"
SERVICES="${SERVICES:-app search db storage}"
mkdir -p "$OUT"
umask 077

if [ ! -f "$OUT/ca.key" ]; then
  openssl ecparam -name prime256v1 -genkey -noout -out "$OUT/ca.key"
  openssl req -x509 -new -key "$OUT/ca.key" -sha256 -days "$CA_DAYS" -subj "/O=HumSaadhak/CN=HumSaadhak internal CA" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" -addext "keyUsage=critical,keyCertSign,cRLSign" -out "$OUT/ca.crt"
fi

for svc in $SERVICES; do
  mkdir -p "$OUT/$svc"
  openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out "$OUT/$svc/tls.key"
  openssl req -new -key "$OUT/$svc/tls.key" -subj "/O=HumSaadhak/CN=$svc" -out "$OUT/$svc/tls.csr"
  printf 'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\nextendedKeyUsage=serverAuth,clientAuth\nsubjectAltName=DNS:%s,DNS:localhost\n' "$svc" > "$OUT/$svc/ext.cnf"
  openssl x509 -req -in "$OUT/$svc/tls.csr" -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" -CAcreateserial -sha256 -days "$DAYS" \
    -extfile "$OUT/$svc/ext.cnf" -out "$OUT/$svc/tls.crt" 2>/dev/null
  rm "$OUT/$svc/tls.csr" "$OUT/$svc/ext.cnf"
  cp "$OUT/ca.crt" "$OUT/$svc/ca.crt"
done

# MinIO reads its certificate from fixed names
if [ -d "$OUT/storage" ]; then
  cp "$OUT/storage/tls.crt" "$OUT/storage/public.crt"; cp "$OUT/storage/tls.key" "$OUT/storage/private.key"
  mkdir -p "$OUT/storage/CAs"; cp "$OUT/ca.crt" "$OUT/storage/CAs/ca.crt"
fi
chmod 644 "$OUT"/ca.crt "$OUT"/*/tls.crt "$OUT"/*/ca.crt
chmod 600 "$OUT"/ca.key "$OUT"/*/tls.key
echo "Certificates written to $OUT (valid $DAYS days). Keep $OUT/ca.key offline; services only need ca.crt and their own tls.*"
