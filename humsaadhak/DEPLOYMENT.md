# Deploying HumSaadhak 2.0

This guide takes a new Ubuntu server to a running, encrypted HumSaadhak in about an hour, most of it waiting for the first
build. One script, `deploy/deploy.sh`, does the work; every step below says which command to run and what to check.

| You need | For |
|---|---|
| An Ubuntu 24.04 LTS (or 22.04) server you can reach over SSH | everything |
| Two DNS names pointing at it, e.g. `humsaadhak.org` and `files.humsaadhak.org` | the site and document downloads |
| A Microsoft 365 tenant where you can register an app | sending email (invitations, password links, notices) |
| `humsaadhak-2.0-all-projects.zip` | the code |

For a trial on your own computer, skip to [Trial on one computer](#trial-on-one-computer).

## What gets deployed

```
Internet ──HTTPS 443──> proxy (Caddy) ──HTTPS──> app (Spring Boot + React)  ──TLS──> db (PostgreSQL 16 + pgvector)
                              │                     │  └──HTTPS──> search (Python, Qwen3 models) ──TLS──> db
                              └──HTTPS (files)──> storage (MinIO: document files, encrypted)
```

| Container | Image | Purpose | Data volume |
|---|---|---|---|
| proxy | caddy:2.8.4 | The only public entry: HTTPS for both domains, Let's Encrypt certificates, http→https | `humsaadhak_caddy-data` |
| app | built from `humsaadhak/` on Amazon Corretto 17 | The web application and API | — |
| search | built from `humsaadhak-search/` | Ranks matches between purposes and needs | — |
| db | pgvector/pgvector:pg16 | All records, sessions, audit trail, search index | `humsaadhak_db-data` |
| storage | built from MinIO's source (`deploy/minio`, release 2025-10-15, commit verified) | Uploaded documents, encrypted at rest | `humsaadhak_storage-data` |
| tls-init | busybox | Gives each service its own internal certificate key at start | `humsaadhak_tls` |
| pgadmin (optional) | dpage/pgadmin4:9.18 | pgAdmin, a browser console for the database; profile `pgadmin`, on for local runs; listens on 127.0.0.1:5050 only | `humsaadhak_pgadmin-data` |

Every connection is TLS 1.2 or 1.3 with the server certificate verified, and the app refuses to start otherwise. The
database refuses unencrypted connections. Bank account numbers (`HUMSAADHAK_FIELD_KEY`) and document files
(`STORAGE_KMS_KEY`) are also encrypted at rest.

## Step 1. Prepare the server

**Size.** Minimum 4 vCPU, 8 GB RAM and 60 GB SSD. Recommended for production: 4 to 8 vCPU, 16 GB RAM, and 100 GB SSD,
plus backup space. The search models need about 3 GB of memory.

**Network.** Inbound: 22 (SSH), 80 and 443 (TCP), and 443/UDP for HTTP/3. Outbound during builds: Docker Hub, Maven
Central, the npm registry, PyPI and Hugging Face, because the search image bakes in its model weights (about 2 GB).

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo apt-get install -y openssl curl unzip ufw
sudo timedatectl set-timezone Asia/Kolkata          # logs and backup names in India time
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
sudo ufw enable
```

Docker publishes only the proxy's ports 80 and 443; no other container is reachable from outside, whatever the firewall says.

## Step 2. Install Docker Engine and the Compose plugin

These are Docker's official packages for Ubuntu; the version in Ubuntu's own repository is too old.

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"     # then log out and back in
docker compose version              # must be 2.20 or newer
```

Docker starts at boot, and every HumSaadhak container has `restart: unless-stopped`, so the site comes back on its own
after a reboot.

## Step 3. Point the DNS names at the server

At your DNS provider, create an A record (and AAAA, if the server has IPv6) for each name:

| Name | Type | Value |
|---|---|---|
| `humsaadhak.org` (your `DOMAIN`) | A | the server's public IPv4 address |
| `files.humsaadhak.org` (your `FILES_DOMAIN`) | A | the same address |

Check with `getent hosts humsaadhak.org` on the server. Let's Encrypt can only issue certificates once both names resolve to this server.

## Step 4. Set up email (Microsoft Graph, or SMTP)

HumSaadhak sends every email through Microsoft Graph as one mailbox, `MAIL_FROM` (for example `support@humsaadhak.com`).
Set this up before the first start: the first super admin receives a set-password email.

1. In the Microsoft Entra admin centre, open **App registrations → New registration**. Name it "HumSaadhak mail" and
   choose single tenant. Note the **Application (client) ID** and **Directory (tenant) ID**.
2. Under **API permissions → Add a permission → Microsoft Graph → Application permissions**, add **Mail.Send**, then
   **Grant admin consent**.
3. Under **Certificates & secrets → New client secret**, copy the secret's **Value**; it is shown only once. Note its
   expiry date, and renew it before then.
4. Limit the app to the one mailbox, so it cannot send as anyone else. In Exchange Online PowerShell, use
   `New-ApplicationAccessPolicy -AppId <client id> -PolicyScopeGroupId <group containing the mailbox> -AccessRight RestrictAccess`,
   or Exchange's RBAC for Applications.

You will put these values into `deploy/.env` in step 6.

**Or SMTP (for example a Microsoft 365 mailbox).** Set `MAIL_PROVIDER=smtp` and the mailbox's server and sign-in:

```
MAIL_PROVIDER=smtp
MAIL_FROM=support@yourdomain.com          # the same mailbox as SMTP_USERNAME (or one it may "Send As")
SMTP_HOST=smtp.office365.com
SMTP_PORT=587                             # STARTTLS, always required; the server's certificate and name are checked
SMTP_USERNAME=support@yourdomain.com
SMTP_PASSWORD=...                         # with a $ in it: SMTP_PASSWORD='...'
SMTP_SSL_TRUST=                           # optional, see below
```

Microsoft 365 needs "Authenticated SMTP" turned on for that mailbox (Microsoft 365 admin centre → Users → the mailbox →
Mail → Manage email apps). Microsoft turns off password sign-in for SMTP (SMTP AUTH Basic) by default for existing
tenants at the end of December 2026, and will announce its final removal in 2027. So SMTP with a password is a stop-gap:
Microsoft Graph (above) signs in with OAuth and keeps working.

Test the settings on their own, without the app: `deploy_local.cmd smtp-test` (or `deploy/deploy.sh smtp-test`) signs in
to the mail server and sends one email to the first super admin. When the server refuses, it says why:

| The server says | It means | What to do |
|---|---|---|
| `535 5.7.3 Authentication unsuccessful` | user name or password refused | check `SMTP_USERNAME` and `SMTP_PASSWORD` (try them at https://outlook.office.com); an account with multi-factor sign-in needs an app password |
| `535 5.7.139 ... SmtpClientAuthentication is disabled` | password sign-in for SMTP is off | turn on Authenticated SMTP for the mailbox (above), or check security defaults and Conditional Access; or use Graph |
| `5.7.60` or `SendAsDenied` | the account may not send as `MAIL_FROM` | set `MAIL_FROM` to `SMTP_USERNAME`, or give it Send As permission |

**"Email sent" in the log, but nothing arrives.** The line `Email sent via SMTP to [address]: "subject" (Message-ID <...>)`
means the mail server accepted the email; delivery from there is the mail server's work. Look, in this order: the
recipient's junk folder; the sender mailbox's inbox, for an "Undeliverable" reply; and the mail server's own record. With
Microsoft 365 that is the Exchange admin centre → Mail flow → Message trace: search for the recipient (or the
Message-ID) to see whether the email was delivered, filtered as spam, or blocked, and why.

`SMTP_SSL_TRUST=smtp.office365.com` sets `mail.smtp.ssl.trust` for that host: its certificate is then accepted without
being verified. The mail is still encrypted, but a forged certificate would be accepted too, so the app logs a warning and
the deploy scripts show one. Leave it empty to have the certificate verified, which works for Microsoft 365. The SMTP
settings map one to one onto Spring's `spring.mail` (host, port, username, password, `mail.smtp.auth`,
`mail.smtp.starttls.enable` and `.required`, `mail.store.protocol: imaps`), set in `application.yml` from these variables.

## Step 5. Put the code on the server

```bash
scp humsaadhak-2.0-all-projects.zip admin@your-server:/tmp/
ssh admin@your-server
sudo mkdir -p /opt && sudo unzip -q /tmp/humsaadhak-2.0-all-projects.zip -d /opt
sudo chown -R "$USER": /opt/humsaadhak-2.0
cd /opt/humsaadhak-2.0/humsaadhak
```

No Java is needed on the server: the app image builds and runs on Amazon Corretto 17. Keep the two folders side by side, as in the zip: `humsaadhak/` builds the app, and `humsaadhak-search/` next to it builds the search service.

## Step 6. Configure

There are two settings files, both git-ignored and mode 600:

| File | Used by | Database | Template |
|---|---|---|---|
| `deploy/.env` | `deploy/deploy.sh <command>` | The local PostgreSQL container (`COMPOSE_PROFILES=local-db`) | `deploy/.env.example` |
| `deploy/.env.prod` | `deploy/deploy.sh --prod <command>` | A managed PostgreSQL, e.g. the existing DigitalOcean cluster (see [below](#production-with-the-existing-digitalocean-database)) | `deploy/.env.prod.example` |

If you received filled `.env` and `.env.prod` files (made from the previous project's settings), copy them into `deploy/`
and run `chmod 600 deploy/.env deploy/.env.prod`. Otherwise create them:

```bash
deploy/deploy.sh init            # deploy/.env
deploy/deploy.sh --prod init     # deploy/.env.prod
```

It asks for the site's domain, the downloads domain, the Let's Encrypt contact email and the first super admin's email.
It then writes `deploy/.env` (mode 600) with a strong random value for every secret. Next, open `deploy/.env` and set
email:

```bash
nano deploy/.env
#   MAIL_PROVIDER=graph
#   MAIL_FROM=support@humsaadhak.com
#   GRAPH_TENANT_ID=…  GRAPH_CLIENT_ID=…  GRAPH_CLIENT_SECRET=…
deploy/deploy.sh check
```

`check` must end without a "failed" line: it reports DNS, ports, memory, disk and every setting.

**Copy `deploy/.env` to your password manager or an offline store now.** `HUMSAADHAK_FIELD_KEY` and `STORAGE_KMS_KEY`
cannot be recovered: without them, stored bank account numbers and document files are unreadable. Also copy
`deploy/tls/out/ca.key` after step 7.

| Setting | Set by | Meaning |
|---|---|---|
| `DOMAIN`, `FILES_DOMAIN` | init | Public names of the site and of document downloads |
| `ACME_EMAIL` | init | Let's Encrypt expiry notices |
| `HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL` | init | On an empty database, the first super admin is created for this address and emailed a set-password link |
| `COMPOSE_PROFILES` | template | `local-db` starts the local database container; empty for a managed database |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | init (local) or you (managed) | The application's database. Locally the password is random; for a managed database, use the provider's credentials. `DB_NAME` must be a new database, never the old system's |
| `DB_ADMIN_DB`, `DB_CA_FILE`, `DB_TOOLS_IMAGE` | template | Where `CREATE DATABASE` runs (`postgres`, or DigitalOcean's `defaultdb`); the CA certificate the database is verified against; the PostgreSQL client tools image for backups |
| `LEGACY_DB_*` | you | The old system's database, read once by `dump-legacy` |
| `SEARCH_TOKEN`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | init (random) | Service credentials, used only inside the server |
| `HUMSAADHAK_FIELD_KEY`, `STORAGE_KMS_KEY` | init (random, 32 bytes) | Encryption at rest; never change them once data exists |
| `MAIL_PROVIDER`, `MAIL_FROM`, `GRAPH_*`, `SMTP_*` | you | `graph` (Microsoft Graph) or `smtp` (an SMTP server, e.g. Microsoft 365) for production; `log` writes emails to the log (trials only) |
| `MAIL_REDIRECT_TO` | you, optional | Sends every email to one inbox: for a staging server, never production |
| `MAIL_LOG_LINKS` | init (trials) | `true` only on localhost trials; `check` refuses it on a real domain |
| `USE_FAKE_MODELS` | you, optional | `true` replaces the search models with a simple stand-in (demos only) |
| `APP_TAG`, `SEARCH_TAG` | leave as `latest` | Image tags; `update` keeps the previous build as `:previous` |

## Step 7. Deploy

```bash
deploy/deploy.sh up
```

What it does:

1. Runs the checks.
2. Makes the internal certificates (`deploy/tls/out`; valid 397 days, renewed by the script).
3. Builds the three images: the app, the search service, and MinIO from its source (MinIO no longer publishes free
   Docker images). The first build takes 20 to 45 minutes, depending on the connection, and downloads about 3.5 GB.
   Downloads are kept in Docker's build cache, and a failed build is retried up to three times, so a rerun resumes.
4. Starts all containers.
5. Waits until the app reports healthy (up to 15 minutes; the search models load on first start).
6. Runs the smoke tests:
   - the site answers over HTTPS, and http redirects to https;
   - HSTS and Content-Security-Policy headers are present;
   - the downloads domain answers;
   - TLS 1.1 is not accepted;
   - the database refuses unencrypted connections.

It ends with `HumSaadhak is running at https://…`. If it stops, the message names the cause, and `deploy/deploy.sh logs app`
shows the app's log.

## Step 8. First sign-in and setup

1. The address in `HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL` receives "Set your password". Open the link (valid 72 hours) and choose
   a password: at least 12 characters, not containing the email.
2. Sign in. Under **Roles and permissions**, review the built-in roles. Under **Users → Add staff member**, add the
   managers and associates with their roles. Each receives a set-password email. Staff never sign up themselves; donors,
   schools and NGOs do, and a manager approves them.
3. Under **Reference data**, check the sectors and each organisation type's mandatory documents.
4. Record the production sender address and the other open decisions (architecture document, section 21).

## Step 9 (optional). Migrate the old system

With the old database's details in `LEGACY_DB_*` (the filled `.env.prod` has the DigitalOcean ones), the script makes the
dump itself, read-only: `deploy/deploy.sh --prod dump-legacy` writes `backups/legacy-<database>-<date>.dump`. Use that file
below instead of making a dump by hand.

Do this first on a copy of the server, or on a trial, and check the report before doing it on production.

```bash
# On the old server: a custom-format dump (plain .sql and .sql.gz also work) and the uploads folder
pg_dump -Fc -d <old database> -f humsaadhak-old.dump
tar czf old-uploads.tar.gz -C /path/to uploads

# On the new server
mkdir -p /srv/legacy-uploads && tar xzf old-uploads.tar.gz -C /srv/legacy-uploads --strip-components=1
chmod -R a+rX /srv/legacy-uploads                      # the app runs as user 10001 and must read the files
deploy/deploy.sh migrate-legacy ~/humsaadhak-old.dump /srv/legacy-uploads
```

The script asks you to type MIGRATE, then:

1. Takes a backup.
2. Loads the dump into the `legacy` schema.
3. Runs the app once with the `legacy-migration` profile: Flyway V100 copies the records, and the file copier moves the
   old files into storage.
4. Restarts the app normally.
5. Prints `legacy_migration_report`.

Check every count and the total paid amount against the old system. If anything is wrong, run
`deploy/deploy.sh restore backups/<…-pre-legacy>`, fix the cause, and run it again.

Copied users keep their old password if it was a real bcrypt hash. Those whose old password was an unhashed temporary
one use "Forgot password" on the sign-in page; tell them before go-live.

## Step 10. Backups and monitoring

```bash
deploy/deploy.sh backup         # try it once now
deploy/deploy.sh cron           # prints the crontab lines; add them with: crontab -e
```

| What | Detail |
|---|---|
| Contents | Database (`pg_dump`, verified by reading it back), document files, `deploy/.env`, the internal certificates, a MANIFEST and SHA256SUMS |
| Where | `humsaadhak/backups/<date-time>/` (mode 700), kept 14 days (`BACKUP_KEEP_DAYS`) |
| Schedule | Nightly at 02:30 from cron; certificate check weekly |
| Off the server | Set `BACKUP_UPLOAD_CMD`, which gets the backup folder as `$1`. Example cron line: `30 2 * * * BACKUP_UPLOAD_CMD='rclone copy "$1" offsite:humsaadhak/$(basename "$1")' ASSUME_YES=1 /opt/humsaadhak-2.0/humsaadhak/deploy/deploy.sh backup` |
| Test | Once a month, restore the latest backup on a trial machine: `deploy/deploy.sh restore <folder> --with-env` |

Backups contain the encryption keys, so store them where only administrators can read them.

Monitoring:

| Watch | How |
|---|---|
| Health | `https://<DOMAIN>/actuator/health` answers `{"status":"UP"}`; point an uptime monitor at it |
| Overview | `deploy/deploy.sh status`: containers, health, certificate expiry, latest backups |
| Logs | `deploy/deploy.sh logs app -f`. Each container keeps 5 × 20 MB of logs |
| Disk | Alert at 80% used: database, files and backups grow over time |
| Security | The **Audit log** page (Super admin, Manager) records every action, including refused attempts |

## Step 11. Go-live checklist

- [ ] `deploy/deploy.sh up` ended with "all smoke tests passed"
- [ ] The site shows a valid certificate in a browser, for both domains (upload and download a document)
- [ ] The first super admin signed in; a test invitation and a password reset email arrived from `MAIL_FROM`
- [ ] `MAIL_REDIRECT_TO` is empty and `MAIL_LOG_LINKS=false`
- [ ] `deploy/.env` and `deploy/tls/out/ca.key` are stored offline; the Graph secret's expiry is in the calendar
- [ ] A backup ran, was copied off the server, and a restore was tested elsewhere
- [ ] Cron lines are installed; an uptime monitor watches `/actuator/health`
- [ ] The legacy migration report was checked (if migrating), and users were told about "Forgot password"

## Running it

| Task | Command |
|---|---|
| New release | Unzip the new release over `/opt/humsaadhak-2.0` (keep `deploy/.env`, `deploy/.env.prod`, `deploy/tls/out`, `deploy/tls/digitalocean-ca.crt` and `backups/`), then `deploy/deploy.sh --prod update` (without `--prod` for a local database) |
| What `update` does | Backup → keep the running images as `:previous` → build → start (Flyway applies new migrations) → health and smoke tests. If the new release is not healthy, it **rolls back by itself**, putting back the previous images and the pre-update data |
| Roll back by hand | `deploy/deploy.sh rollback` returns to the release before the last update, with the data as it was just before it. Changes made since the update are lost |
| Internal certificates | Renewed automatically by the weekly cron line when fewer than 30 days remain; by hand: `deploy/deploy.sh renew-certs`. Public certificates are renewed by Caddy |
| Stop / start | `deploy/deploy.sh down` / `deploy/deploy.sh up` (data is kept) |
| Restore | `deploy/deploy.sh restore backups/<folder>` (type RESTORE). It refuses a backup whose encryption keys differ from `deploy/.env`; add `--with-env` to restore its `.env` and certificates too |
| Change a password or secret | Edit `deploy/.env`, then `deploy/deploy.sh up`. Never change `HUMSAADHAK_FIELD_KEY` or `STORAGE_KMS_KEY` once data exists. To change `DB_PASSWORD`, first run `ALTER USER humsaadhak PASSWORD '…'` in the database |
| Remove everything | `deploy/deploy.sh destroy` (type `humsaadhak`): deletes the database and files; `.env`, certificates and backups stay |

## The matching engine: what the percentage means, and how accurate it is

Match Studio suggests causes and projects for a donor's purpose by meaning, not by shared words. Two pretrained models
run inside the `search` service, on this server (nothing is sent elsewhere): Qwen3-Embedding-0.6B finds candidates whose
meaning is close, and Qwen3-Reranker-0.6B reads each purpose-candidate pair and judges whether the donation would pay for
that help. Both are told what a match means here (the same kind of help, for the same people, in Indian schools and
NGOs), and local terms are spelled out for them (ZP school, anganwadi, KGBV, CSR Schedule VII, 80G, divyang and others:
`app/util/glossary.py` in the search service).

**The percentage** is how strongly the reranker judges the pair a match: 85% and up "strong", 70-84% "good", 50-69%
"possible"; below 50% is not shown. Once reviewers have decided 200+ suggestions, `python -m training.fit_calibration`
in the search container re-fits it so that "70%" means about 70% of such suggestions were approved.

**How accurate it is:** `deploy_local.cmd match-eval` (`deploy/deploy.sh match-eval` on the server) runs 16 donor
purposes against 22 causes and projects judged by hand (`training/golden_set.py`), worded the way donors write, and
prints how often the top match is right, how often a right match is in the top 3, and the average percentage of right
and unrelated matches. It uses a separate test index: your data is not read or changed.

**Teaching it your decisions:** after 100+ approved matches, `deploy_local.cmd match-train` fine-tunes the embedding
model on them and saves it in the `search-models` volume. Set `EMBEDDING_MODEL=/models/humsaadhak-embedding` in the
settings file and run `deploy_local.cmd` again (the index re-embeds itself), then compare `match-eval` with the earlier
result; to go back, empty `EMBEDDING_MODEL`. After an engine change, use "Run engine for this purpose" again: suggestions
still waiting for review take the new scores; approved and rejected ones keep what the reviewer saw.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `up` stops at "not reachable" or Docker errors | Docker is not running (`sudo systemctl start docker`), or your user is not in the `docker` group (log out and back in) |
| Certificate errors in the browser, or the proxy log shows ACME failures | DNS does not point at this server yet, or ports 80/443 are blocked. Check `getent hosts <DOMAIN>` and the firewall; Caddy retries by itself |
| The app is not healthy after 15 minutes | `deploy/deploy.sh logs app`. "Transport security" errors: `deploy/tls/out` is missing or was regenerated without `renew-certs`. Flyway errors: restore the last backup and report the migration |
| The search service restarts | Not enough memory for the models (8 GB minimum). Check with `deploy/deploy.sh logs search` |
| Uploads fail | `deploy/deploy.sh logs app` and `logs storage`. A KMS error means `STORAGE_KMS_KEY` changed or is missing; the bucket is created by the app at start |
| Document downloads do not open | `FILES_DOMAIN` must resolve to the server and have a certificate. `deploy/deploy.sh smoke` checks it |
| No emails arrive | `MAIL_PROVIDER` must be `graph`; the Graph secret may have expired; `Mail.Send` needs admin consent and must cover `MAIL_FROM`. The app log shows the Graph error |
| A port is in use | Another web server is running: `sudo systemctl disable --now nginx apache2` |
| `pull access denied for minio/minio` | An older copy of the deployment files: MinIO removed its free images from Docker Hub. Use this release, which builds MinIO from source (`deploy/minio/Dockerfile`) |
| The build stops with a timeout | The connection dropped during a download. Run it again: finished downloads are kept in the build cache, and each build is retried three times |

## Trial on one computer

On a Linux computer or WSL2 with Docker and 8 GB of memory:

```bash
cd humsaadhak-2.0/humsaadhak
DOMAIN=localhost HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL=you@example.org ASSUME_YES=1 deploy/deploy.sh init
deploy/deploy.sh up
deploy/deploy.sh admin-link          # the first super admin's set-password link, from the log
```

Open https://localhost, and accept the local certificate warning or trust Caddy's local CA. On a trial, `init` sets
`MAIL_PROVIDER=log` and `MAIL_LOG_LINKS=true`, so emails are written to the log and nobody receives real mail. Without
Docker at all, `preview/humsaadhak-preview.html` shows the whole app on demo data.

## From Windows: deploy_local.cmd and deploy_prod.cmd

Two double-clickable files in the `humsaadhak` folder do the deployment from a Windows 10/11 computer. They need only
what Windows has, plus Docker Desktop: PowerShell, `curl.exe`, `tar.exe` and the OpenSSH client. Both start
`deploy\deploy.ps1`, and the window stays open when double-clicked.

| File | What it does |
|---|---|
| `deploy_local.cmd` | The whole stack on this computer with `deploy\.env` (local database container). It builds, starts, waits until healthy and runs the smoke tests, then you open https://localhost. Without `deploy\.env` it makes one with random secrets. Also: `deploy_local.cmd status`, `logs app`, `logs app -Follow`, `down` |
| `deploy_prod.cmd` | Production with `deploy\.env.prod`. With `DEPLOY_SSH_HOST` set, it packs the release (both projects, with `.env.prod` and `tls\digitalocean-ca.crt`, without build output or local settings), uploads it to the server and runs `deploy/deploy.sh --prod` there: `up` the first time, then `update`, which rolls back by itself if unhealthy. Without `DEPLOY_SSH_HOST` it runs production on this computer, after you type yes. Also, on the server: `deploy_prod.cmd status`, `logs app`, `backup`, `rollback`, `cron`, `smoke` |

Setting up the production server once (step 1 and 2 above on the server, then on Windows):

```powershell
ssh-keygen -t ed25519 -f $HOME\.ssh\humsaadhak_prod                       # a key just for deployments
type $HOME\.ssh\humsaadhak_prod.pub | ssh root@SERVER "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

Then in `deploy\.env.prod`, set `DEPLOY_SSH_HOST` (the server's IP or name), `DEPLOY_SSH_USER` (it must be allowed to
use Docker), `DEPLOY_SSH_KEY=C:\Users\you\.ssh\humsaadhak_prod`, and if needed `DEPLOY_SSH_PORT` and `DEPLOY_DIR`
(default `/opt/humsaadhak-2.0`). Save the DigitalOcean CA certificate as `deploy\tls\digitalocean-ca.crt`, and run
`deploy_prod.cmd`. If Notepad saves a settings file with Windows line endings, the script converts it to LF, because Docker
would otherwise read every value with a trailing `\r`.

## pgAdmin, the database console

pgAdmin 4 runs next to the database when `COMPOSE_PROFILES` contains `pgadmin`. That's on in `deploy/.env` (local runs)
and off in `deploy/.env.prod` unless you add it. It listens on `127.0.0.1:5050` only, so it is never reachable from the
internet, and it needs its own sign-in: `PGADMIN_EMAIL` and `PGADMIN_PASSWORD` from the settings file, which setup fills
in (and `deploy_local.cmd` adds to an older `deploy\.env` by itself).

| Where | How to open it |
|---|---|
| This computer | `deploy_local.cmd pgadmin` (opens http://localhost:5050), or `deploy/deploy.sh pgadmin` |
| A production server | Add `pgadmin` to `COMPOSE_PROFILES` in `deploy/.env.prod` and deploy. Then `deploy_prod.cmd pgadmin` prints the SSH tunnel command (`ssh -N -L 5050:127.0.0.1:5050 user@server`); keep it running and open http://localhost:5050 |

The HumSaadhak database is already registered, under the group HumSaadhak. Use it with full certificate checks
(`sslmode=verify-full` against the internal CA, or DigitalOcean's for a managed database). On first connect pgAdmin asks
for the database password: `DB_PASSWORD` from the same file (tick "Save password" to keep it). pgAdmin reads this
registration on its first start only; after changing the database settings, delete its data with
`docker volume rm humsaadhak_pgadmin-data` (pgAdmin stopped) to register it again.

pgAdmin can change or delete any data. Give its sign-in only to administrators, and in production switch it on only
while you need it.

## Only the database, for development

To run the backend from an IDE or with `mvn -f backend spring-boot:run` (Amazon Corretto 17 and Maven), start just the
database in Docker. It uses the database name, user and password from `deploy/.env`; without that file it uses
`humsaadhak` and a random password, kept in `deploy/.env.db`:

```bash
deploy/db.sh start                          # Linux, macOS, WSL, Git Bash
eval "$(deploy/db.sh env)" && mvn -f backend spring-boot:run
```

```powershell
.\deploy\db.ps1 start                       # Windows PowerShell with Docker Desktop
.\deploy\db.ps1 env | Invoke-Expression; mvn -f backend spring-boot:run
```

The container `humsaadhak-db-dev` runs PostgreSQL 16 with pgvector on `127.0.0.1:5432`, so nothing outside the computer
can connect; its data lives in the volume `humsaadhak-db-dev-data`. `-Port 5433` / `DB_PORT_LOCAL=5433` changes the port.
`-Tls` / `DB_TLS=1` serves TLS only, with the internal certificates (the app then uses `sslmode=verify-full`). `env` also
sets the backend's other settings from `deploy/.env`. Other commands: `status`, `psql`, `logs`, `dump`, `restore <file>`,
`load-legacy <old dump>` (puts the old system's data into schema `legacy` for one run with the `legacy-migration`
profile), `stop` and `reset`. Document uploads need the storage service, which only `deploy/deploy.sh up` starts.

## Production with the existing DigitalOcean database

The previous project ran on a DigitalOcean managed PostgreSQL cluster in Bangalore. `deploy/.env.prod.example` and the filled
`deploy/.env.prod` use that cluster for production: no local database container runs, and the cluster's own daily
backups and failover apply. The new system gets **its own database, `humsaadhak`, on the cluster**. The old database is
left untouched and only read once, by `dump-legacy`; the new schema must never be built inside it, and the script refuses
if `DB_NAME` points at it.

1. **CA certificate.** In the DigitalOcean control panel, open **Databases → the cluster → Connection details** and click
   **Download CA certificate**. Save it as `deploy/tls/digitalocean-ca.crt` (that is `DB_CA_FILE`). Every connection then
   verifies the cluster's certificate and name (`sslmode=verify-full`).
2. **Trusted sources.** On the cluster's **Settings → Trusted sources**, add this server (its droplet or public IP).
   Otherwise the connection is refused.
3. **Version.** The **Overview** tab shows the PostgreSQL version. For 17, set `DB_TOOLS_IMAGE=postgres:17` in `deploy/.env.prod`;
   `up` stops with exactly this advice if the tools are older than the server.
4. **Deploy.** Run `deploy/deploy.sh --prod check`, then `deploy/deploy.sh --prod up`. It connects with `doadmin` to
   `defaultdb`, creates the database `humsaadhak` if it is missing, and starts the app. Flyway then creates the schema and
   the `vector` extension (pgvector is available on DigitalOcean PostgreSQL).
5. **Migrate the old data.** Run `deploy/deploy.sh --prod dump-legacy`, then
   `deploy/deploy.sh --prod migrate-legacy backups/legacy-saadhak_demo-<date>.dump /srv/legacy-uploads`. First check in
   **Connection pools** which database the pool `saadhak_connection_pool` used: if it is not `saadhak_demo`, set
   `LEGACY_DB_NAME` to it.
6. **Backups.** `deploy/deploy.sh --prod backup` (nightly from `deploy/deploy.sh --prod cron`) dumps the `humsaadhak`
   database through the same verified connection, with the files, settings and certificates. DigitalOcean's own backups
   cover the cluster as well.

Use the cluster's direct port (25060), not the connection pool (25061): migrations and dumps need a direct connection.

## Production on AWS (instead of one server)

The same images run on AWS; nothing in the code changes.

| Here | On AWS |
|---|---|
| proxy (Caddy) | Application Load Balancer with an ACM certificate (HTTPS listener, TLS 1.2+ policy) |
| db | Amazon RDS for PostgreSQL 16 with `CREATE EXTENSION vector`, `rds.force_ssl=1`, and automated backups with point-in-time recovery |
| storage (MinIO) | An S3 bucket with default encryption, Block Public Access, and a policy denying `aws:SecureTransport=false`. Set `STORAGE_ENDPOINT` empty, `STORAGE_CREATE_BUCKET=false`, and an IAM role instead of keys |
| app, search | ECS on Fargate (or EKS) services with the two images from ECR; app 2 vCPU / 4 GB; search 2 vCPU / 8 GB |
| deploy/.env | AWS Secrets Manager, injected as environment variables; keep `REQUIRE_TLS=true` |

## Security notes

**Credentials of the previous project: rotate them before go-live.** The previous project's source code contained live
secrets in plain text, so anyone who has had that code can use them. The filled `.env.prod` still carries some of them, so
the system can be deployed; change each one in its own console and then in `deploy/.env.prod`:

| Credential (found in) | Where to change it |
|---|---|
| DigitalOcean database `doadmin` password (`saadhak-app-*/application.yaml`) | Databases → cluster → Users & Databases → reset the password. Better, add a user just for HumSaadhak that owns only the `humsaadhak` database |
| Microsoft Graph client secret (`application.yaml`, `azure.client-secret`) | Entra admin centre → App registrations → the app → Certificates & secrets: create a new secret, delete the old one |
| GitHub personal access token (`saadhak-config-server/application.yaml`) | GitHub → Settings → Developer settings → Personal access tokens: revoke it |
| Root SSH password of the old server (`humsaadhak-2026-main/deploy.sh`) | Change it, and turn off password logins (`PasswordAuthentication no`; SSH keys only) |
| Brevo SMTP password (`application.yaml`, never used) | Brevo → SMTP & API: delete the key |
| JWT signing key (`application.yaml`) | Not used by the new system; it dies with the old one |

Remove these values from the old repository's files and history (for example with `git filter-repo`). Revoking them is
what makes them harmless, though.


- Only ports 80 and 443 are public. The database, search service and storage have no published ports.
- No default passwords exist anywhere. The first super admin sets their own password through a single-use link.
- The internal CA key (`deploy/tls/out/ca.key`) is only needed to issue new internal certificates; keep it offline.
- `deploy/.env`, `deploy/.env.prod`, `deploy/tls/out` and `backups/` must never be committed or shared; `.gitignore` excludes them.
