# HumSaadhak 2.0: all projects

| Folder | What it is | Start here |
|---|---|---|
| `humsaadhak/` | The platform: Spring Boot 4.1 backend (Java 17 on Amazon Corretto, 13 modules), responsive React 19 frontend on Bootstrap 5.3, PostgreSQL migrations V1–V7 and the legacy migration, deployment with TLS on every connection | `humsaadhak/README.md` |
| `humsaadhak/DEPLOYMENT.md` | **Complete deployment steps**, including production on the existing DigitalOcean database | read before deploying |
| `humsaadhak/deploy/deploy.sh` | **The deployment script**: init, up, status, backup, restore, update with automatic rollback, dump-legacy, migrate-legacy, certificates | `deploy/deploy.sh help` |
| `humsaadhak/deploy/.env.example`, `.env.prod.example` | Settings templates: local (database container) and production (managed database). The filled files come separately (`humsaadhak-env-files.zip`) because they hold passwords | DEPLOYMENT.md, step 6 |
| `humsaadhak/deploy_local.cmd`, `deploy_prod.cmd` | **Windows, double-click**: the whole stack on this PC (Docker Desktop, `deploy\.env`), or production (`deploy\.env.prod`) uploaded over SSH to the server and deployed there | DEPLOYMENT.md, "From Windows" |
| `humsaadhak/deploy/db.sh`, `db.ps1` | **Only the database in Docker, for development**: PostgreSQL 16 + pgvector with the settings from `deploy/.env` (bash; Windows PowerShell) | `deploy/db.sh help` |
| pgAdmin | Database console in the stack (profile `pgadmin`; on for local runs at http://localhost:5050, 127.0.0.1 only; SSH tunnel in production) | `deploy_local.cmd pgadmin` |
| `humsaadhak-search/` | The semantic search service (Python 3.12, FastAPI, Qwen3 embedding and reranking) that ranks matches | `humsaadhak-search/README.md` |
| `docs/HumSaadhak_2.0_Architecture.docx` | Architecture: HLD, LLD, database design and a flowchart for every functionality (21 sections, 30 diagrams) | open in Word |
| `preview/humsaadhak-preview.html` | The whole app on demo data in one file: sign in as any role; fits any screen | open in a browser |

Keep `humsaadhak/` and `humsaadhak-search/` side by side: the deployment builds both.

## Deploy (details in humsaadhak/DEPLOYMENT.md)

On Windows: unzip `humsaadhak-env-files.zip` here, then double-click `humsaadhak\deploy_local.cmd` (this PC) or
`humsaadhak\deploy_prod.cmd` (production server, after setting `DEPLOY_SSH_HOST` in `deploy\.env.prod`). On Linux:

```bash
cd humsaadhak
unzip ../humsaadhak-env-files.zip -d ..        # puts deploy/.env and deploy/.env.prod in place (or: deploy/deploy.sh init)
chmod 600 deploy/.env deploy/.env.prod
# Production on the DigitalOcean database: save its CA certificate as deploy/tls/digitalocean-ca.crt and add this server
# to the cluster's trusted sources (DEPLOYMENT.md, "Production with the existing DigitalOcean database").
deploy/deploy.sh --prod up                     # production (deploy/.env.prod); without --prod: local (deploy/.env)
deploy/deploy.sh --prod cron                   # crontab lines for nightly backups and certificate checks
```

Before go-live, rotate the previous project's credentials (DEPLOYMENT.md, Security notes): they were stored in its source code.

## Verified in this build

| Check | Result |
|---|---|
| Backend unit tests, compiled with javac 17 and run on Java 17, including architecture rules, TLS handshakes and deployment settings | 115 / 115 pass |
| Frontend type-check and tests (every screen and role, exports, themes, roles, audit trail, responsive layout, adding staff, sign-in animations, page flips, compulsory documents, all documents for reviewers) | 104 / 104 pass |
| Search service tests, including the Docker packaging rules | 36 / 36 pass |
| Deployment script: shellcheck; local and production settings checked; up, backup, update, rollback, restore, dump-legacy and migrate-legacy dry-run | clean; behave as documented |
| Production frontend build excludes demo data; `npm audit` | confirmed; 0 vulnerabilities |
| Spring and database tests (40) | written; run `mvn -B verify` on a machine with Maven Central and Docker before the first release |
