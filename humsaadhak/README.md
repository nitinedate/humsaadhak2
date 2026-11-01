# HumSaadhak 2.0

One Spring Boot application (modular monolith) that serves the REST API **and** the React app, plus a separate
Python service for semantic search (`../humsaadhak-search`). Replaces the six Spring Cloud services and the Next.js
template app. Design rationale: *HumSaadhak 2.0 — Target Architecture & Restructuring Blueprint*.

```
humsaadhak/
├─ backend/                 Spring Boot 4.1 · Java 17 (Amazon Corretto) · Spring Modulith · PostgreSQL + Flyway
│  ├─ src/main/java/org/humsaadhak/<module>/       one package per business module (table below)
│  ├─ src/main/resources/db/migration/             V1 schema · V2 reference data · V3 preferences, history, imports
│  └─ src/main/resources/db/legacy/                V100 one-time migration from the old database
├─ frontend/                React 19 · TypeScript · Vite · TanStack Query · Recharts
│  └─ src/app · src/features/<screen> · src/shared · src/styles (themes.css, global.css)
├─ deploy/                  Docker Compose (app, search, PostgreSQL + pgvector, MinIO) and .env template
├─ .github/workflows/ci.yml type-check, tests and build on every push
└─ Dockerfile               builds the React app into the Spring Boot JAR → one image
```

## Run it

| Goal | Command |
|---|---|
| See the UI with sample data, no backend | `cd frontend && npm ci && npm run demo` → http://localhost:5173 |
| Deploy to a server (or a trial on one computer), encrypted end to end | `deploy/deploy.sh init`, then `deploy/deploy.sh up` → https://DOMAIN; production with the managed (DigitalOcean) database: `deploy/deploy.sh --prod up` with `deploy/.env.prod`. **Full steps: [DEPLOYMENT.md](DEPLOYMENT.md)**; `deploy/deploy.sh help` lists backup, update, rollback, restore, legacy migration and certificate commands |
| Backend in the IDE | PostgreSQL with pgvector; set `DB_PASSWORD`, `SEARCH_TOKEN`, `HUMSAADHAK_FIELD_KEY`; run `HumSaadhakApplication` |
| Frontend against that backend | `cd frontend && npm run dev` (proxies `/api` to :8080) |
| From Windows (double-click) | `deploy_local.cmd`: the whole stack on this PC with Docker Desktop and `deploy\.env`. `deploy_prod.cmd`: production with `deploy\.env.prod`, uploaded over SSH to the server in `DEPLOY_SSH_HOST` and deployed there with `deploy.sh --prod` (see DEPLOYMENT.md, "From Windows") |
| pgAdmin (database console) | On for local runs: `deploy_local.cmd pgadmin` opens http://localhost:5050 (bound to 127.0.0.1; sign-in `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` from `deploy/.env`; the database is pre-registered with full TLS checks). In production: profile `pgadmin` and an SSH tunnel (`deploy_prod.cmd pgadmin`) |
| Only the database, for development (backend from the IDE or `mvn spring-boot:run`) | `deploy/db.sh start`, then `eval "$(deploy/db.sh env)"` (Linux, macOS, WSL, Git Bash); on Windows PowerShell: `.\deploy\db.ps1 start`, then `.\deploy\db.ps1 env \| Invoke-Expression`. PostgreSQL 16 + pgvector on 127.0.0.1:5432 with the database, user and password from `deploy/.env`; `help` lists dump, restore, psql, load-legacy and reset |
| One JAR with the UI inside | `mvn -B package` from this folder (frontend first, then backend). Needs **Amazon Corretto 17** (`java -version` shows Corretto 17) and Maven 3.9; any Java 17 works, newer versions are not required |

**First start on an empty database:** set `HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL`. The first super admin is created for that
address and emailed a single-use set-password link. There is no default password anywhere.

**Required secrets** (environment only, never in files): `DB_PASSWORD`, `SEARCH_TOKEN`, `HUMSAADHAK_FIELD_KEY`
(`openssl rand -base64 32`; encrypts bank account numbers, so back it up), storage keys, and for production email the
Microsoft Graph client id/secret. In development `MAIL_PROVIDER=log` writes emails to the log instead of sending them.

## Encryption in transit

Every connection carries data encrypted with TLS 1.3 or 1.2, and every client verifies the server's certificate and
hostname (encrypted but unverified connections are not accepted, because they can be intercepted).

| Connection | How it is protected |
|---|---|
| Browser → site | Caddy (`deploy/Caddyfile`) serves HTTPS with automatic Let's Encrypt certificates, redirects `http://` to `https://`, TLS 1.2/1.3 only. The app sends HSTS (one year, sub-domains) and `upgrade-insecure-requests`; session cookies are `Secure`, `HttpOnly`, `SameSite=Lax` |
| Browser → document downloads | Signed links point at `FILES_DOMAIN` (`STORAGE_PUBLIC_ENDPOINT`), served over HTTPS by Caddy; with AWS S3 they are `https://…amazonaws.com` links |
| Proxy → app | Re-encrypted: the app serves HTTPS itself (profile `tls`, port 8443, TLS 1.3/1.2); Caddy verifies its certificate |
| App → PostgreSQL, search → PostgreSQL | `sslmode=verify-full` with the internal CA; PostgreSQL runs with `ssl=on`, TLS 1.2 minimum, and `pg_hba.conf` rejects every unencrypted network connection |
| App → search service | `https://search:8000`; the search service serves only HTTPS; the app's client offers TLS 1.3/1.2 only and checks certificate and hostname |
| App → MinIO / S3 | `https://`; the S3 client trusts the internal CA (MinIO) and the public CAs (AWS) |
| App → Microsoft Graph (email) | `https://` with public certificates |

Only the proxy is reachable from outside; the other containers have no published ports.

**Internal certificates** come from `deploy/tls/make-certs.sh`: a small internal CA (ECDSA P-256) and one certificate per
service, each naming its service so hostnames can be checked. Re-run it before the certificates expire (397 days); the CA
is reused, so nothing else changes. Keep `deploy/tls/out/ca.key` offline. Each container can read only its own key
(`tls-init` gives it to that service's user with mode 0400).

**Production refuses to start unencrypted.** With `REQUIRE_TLS=true` (set in `deploy/compose.yaml`) the app refuses to
start if the public address is not `https://`, it does not serve HTTPS itself, cookies are not `Secure`, the database URL
lacks `sslmode=verify-full`, or the search service or file storage is configured with `http://`
(`TransportSecurityPolicy`, `TransportSecurityCheck`). The search service does the same for its own HTTPS and its
database URL.

**On AWS:** put the app behind an Application Load Balancer with an ACM certificate and the
`ELBSecurityPolicy-TLS13-1-2-2021-06` policy, forwarding to the app over HTTPS; use RDS with `rds.force_ssl=1` and
`sslmode=verify-full&sslrootcert=<RDS CA bundle>`; deny non-TLS access to the S3 bucket with a policy on
`aws:SecureTransport = false`; and keep the search service on HTTPS inside the VPC.

**Trying it on one machine:** set `DOMAIN=localhost` and `FILES_DOMAIN=files.localhost`. Caddy then issues local
certificates; run `docker compose exec proxy caddy trust` (or trust Caddy's root certificate in your browser) to avoid
the browser warning.

Verified by tests that make real TLS connections: `TlsSupportTest` (Java) and `tests/test_tls.py` (search service). They
check that the internal CA is trusted, that public-CA-only clients are refused, that a certificate from another CA is
refused, that a wrong hostname is refused, and that TLS 1.1 is refused while 1.2 and 1.3 work. `TransportSecurityPolicyTest`
and `TransportSecurityCheckTest` cover the start-up guard.

## Who is who

Every screen and dashboard uses the same four groups: **Individual donors** and **Corporate donors** (benefactors) give to
**Direct beneficiaries** (schools; legacy "DB") and **Indirect beneficiaries** (NGOs; legacy "IB"). The manager and
associate dashboards show all four, including a "who gave to whom" chart; donor dashboards split giving between direct
and indirect beneficiaries; beneficiary dashboards split money received between individual and corporate donors.
The names live in one place on each side: `reporting/BeneficiaryTerms.java` and `ROLE_LABEL` in `frontend/src/shared/format.ts`.

## Roles and permissions

Super admins manage access under **Roles and permissions** (permission `admin.roles`):

- **Roles** list each role with its type of account, people and permissions; a role opens into a checklist of permissions grouped
  by area. New roles are created with a name, a type of account (HumSaadhak staff with the platform or the work dashboard,
  individual or corporate donor, school, NGO) and optionally the permissions of an existing role to start from.
- **Permissions** list every permission and which roles grant it. Built-in permissions are checked by the application; new ones
  (codes like `field-visits.approve`) can be granted to roles straight away and are part of each person's rights
  (`/me.permissions`, Spring authority `perm:<code>`), so a feature or integration can check them by code.
- **Change log** records who created, changed or deleted what, and every change of a person's role.
- **Users** offers every role (built-in or custom) that fits the person's account: staff roles for staff, otherwise roles of
  the same account type.

Rules (`identity/RolePolicy.java`): built-in roles and permissions cannot be deleted or renamed; the Super admin role always
keeps "Manage users" and "Manage roles and permissions", so nobody can lock the platform out; a role in use cannot be
deleted; people whose role or role permissions change are signed out everywhere, so new rights apply at once.

Storage (`V6__roles_permissions.sql`): `app_role`, `app_permission`, `app_role_permission`, `access_change_log`, and
`app_user.role_code`. The account type stays in `app_user.role` (it decides data scope and dashboard). The previous fixed roles
are seeded as built-in roles with exactly their old permissions (`RoleSeedTest` checks this). Spring Session's tables are now
created by this migration instead of at start-up. The signed-in principal is a small serialisable snapshot; it previously held
the JPA entity, which cannot be serialised into the session store.

## Audit trail

Every action a person takes is recorded in `audit_event`: who (name, email and role at the time), what (`purpose.update`,
`approval.reject`, `document.download` …), which record, when, from which IP address and browser, and whether it was done,
refused or failed, with a short note such as a rejection reason or an amount. Sign-ins (failed ones and lockouts too), sign-outs,
registrations, password links, downloads, report runs and exports are included. Passwords, tokens and file contents never are.

- **Where people see it:** an **Audit trail** button on each record (purposes, causes and projects, documents, approvals, users,
  roles, sectors, imports, document requests, Match Studio, the organisation profile) shows who did what to it and, for audited
  entities, which fields each action changed (old → new). Each page has an **Audit trail for this page** panel with the recent
  actions in its area; Settings has **My activity**. Staff with `audit.view` (Super admin and Manager) also get the
  **Audit log** page (`/admin/audit`): every action with filters, refused and failed attempts, addresses, and CSV export.
- **Who sees which trail:** staff see every record's trail; donors, schools and NGOs see the trails of records their organisation
  owns or shares (a donation or a document request belongs to both sides). Refused and failed attempts, IP addresses and
  browsers are shown only with `audit.view`. Each module says who owns its records through `shared/security/AuditOwnership`.
- **How it is recorded:** controller methods carry `@AuditAction("purpose.update", target = PURPOSE, id = "#id")`.
  `audit/service/AuditMethodInterceptor` runs the action and writes its audit row **in one transaction**, so no change is ever
  committed without its record; a refused or failed attempt rolls back and is recorded on its own (requests rejected before the
  method, such as invalid input, by `AuditRequestInterceptor`). Each request has an id, returned in `X-Request-Id` and stamped on
  the Envers revisions it writes (`revinfo.request_id`), which is how a trail shows the fields each action changed.
- **Cannot be tampered with:** database triggers refuse any update, delete or truncate of `audit_event`, even from the application.
- **Cannot be forgotten:** `LayeredArchitectureTest.everyStateChangingEndpointIsAudited` fails the build if any endpoint that
  changes data, or any download or export, has no `@AuditAction`.

Endpoints: `GET /api/v2/audit/trail?type=&id=`, `GET /api/v2/audit/activity?category=&mine=`, `GET /api/v2/audit/events`
and `/audit/events.csv` (`audit.view`). Storage: `V7__audit_trail.sql`.

## Reports (all reports of the original project)

| Original (`/api/v1/report/…`) | Now | Columns |
|---|---|---|
| `donation_report` | Donation report | Cause/Project, Donor Name, Donor Type, Commitment Amount, Commitment Date, Periodicity, Donation Amount, Donation Date |
| `match_report` | Match report | Cause/Project, Purpose ID, Purpose Name, Commitment Amount, Commitment Date, Periodicity |
| `project_report`, `cause_report` | Project report / Cause report | Name, Approved Date, Matched (Y/N), Donation Received (Y/N), Sum of Commitment Amount (matched), Sum of Donation Amount |
| `custom_project_report`, `custom_cause_report` | Customize project / cause report | Any of the 16 original columns: Project (or yet to be approved), Approved Date, Matched Flag, Purpose Id, Purpose Name, Commitment Amount/Date, Donation Amount/Date, Donor Name, Donor Type, Upcoming Donation Amount/Date, Doc status, Periodicity |
| `report_page_filter_details` | `GET /api/v2/beneficiary-reports/options` | the organisation, its causes or projects, financial years, custom columns |
| `export/excel`, `export/pdf` | Export as Excel / Export as PDF | logo, report title (the custom report's Header), "Entity Name - … [ID: …]", the filter line, then the table |

`POST /api/v2/beneficiary-reports/{donations|matches|needs|custom}` with the original filters (cause or project, financial
year, approved start and end dates), paged. Schools report on their causes and NGOs on their projects; staff choose any school
or NGO; donors cannot run them (as in the original). Excel (with Indian ₹ grouping) and PDF files are built in the browser from
the full result.

What changed on purpose: the custom report no longer builds SQL from the `column_mapping`/`join_mapping` tables (finding S7);
its columns and joins are constants in `reporting/BeneficiaryReportQuery.java` and the request can only switch them on. The
project/cause totals no longer double-count (the original joined matches and donations in one query); the financial year runs
1 April to 31 March (the original used 31 March to 31 March); cause/project approval dates and purpose commitment dates are
stored (`V5__report_dates.sql`, filled from the old data by the migration). Staff also keep the whole-table CSV exports.

## Chart details (as in the original project)

Every donut slice, legend row and bar opens a dialog with the records behind it (`GET /api/v2/dashboards/details`).
Each segment of `/api/v2/dashboards/me` carries a `drill` (set + filters); the dialog sends it back unchanged. Sets are
organisations, causes and projects, donations and purposes; rows open into related records (a beneficiary into its causes
or projects, a donor into its donations, a cause or project into the donations it received, a purpose into its matches).
Filters are allow-listed and bound as parameters (`reporting/DetailQuery.java`), and the person's scope is always added
on the server: donors see only their own giving, beneficiaries only their own organisation, staff everything.
Keyboard users reach the same dialogs through buttons that appear under each bar chart when focused.

## Screens (React)

| Who | Screens |
|---|---|
| Everyone | Sign in · 3-step sign-up · set password (invitation or reset link) · forgot password · role dashboard with charts and **Download as CSV** · **Settings with 4 themes** |
| Donors | My giving (confirmed matches, request documents, give) · My purposes · record donation · document requests · organisation profile |
| Schools / NGOs | Our causes / Our projects (funding progress, donors whose purposes fit) · documents (upload, versions, rejection reasons) · document requests (share or decline) · organisation profile and bank accounts |
| Associates / managers | Approvals inbox · Match Studio · purposes and needs for any organisation · document review queue · change history on every record · Reports (CSV downloads of donations, organisations, causes and projects) |
| Super admins | Users (add staff members, search, role, disable/enable, resend invitation) · Roles and permissions (create roles and permissions, grant permissions, change log) · bulk Excel import · Reference data (sectors, document types, and which documents each group must provide; changes re-check verification) |

**Responsive (Bootstrap 5.3.8).** Every screen works from a 360-pixel phone to a 2,560-pixel monitor. Pages have no fixed maximum
width: they fill whatever the screen leaves beside the side panel (checked at 1,280, 1,366, 1,536, 1,920 and 2,560 px, never
wider than the screen). From 992 px the left panel stays fixed while the page scrolls; below that a top bar with a menu button opens it as a Bootstrap off-canvas menu (it closes
on a link, Escape or a tap outside). Headline figures go four across on desktops, two by two on tablets and one per row on
phones; wide tables scroll sideways inside `.table-responsive`; drawers and dialogs take the full width on phones. Bootstrap's
CSS is used (its JavaScript is not: React drives the menu and dialogs); `src/styles/bootstrap-theme.css` sets Bootstrap's
variables from the theme tokens, so every theme applies, and Night turns on Bootstrap's dark mode. `frontend/.npmrc` sets
`legacy-peer-deps` because npm 10 cannot resolve Vitest 5's optional peer packages; the Dockerfile copies it before `npm ci`.

Themes — Lavender
(default), HumSaadhak sand, Ocean mint and Night — change instantly and are saved on the account (`PUT /api/v2/me/preferences`),
so they follow the person to any device; the last theme is also cached in the browser to avoid a flash on load.

## Backend structure (`backend/src/main/java/org/humsaadhak`)

A modular monolith: one Spring Boot application split into business modules (`identity`, `registry`, `giving`, …).
Inside every module the code is layered the same way, as in a classic Spring MVC application:

```
org.humsaadhak.<module>
├── api          the module's public interface (facades, events, shared types) — the only part other modules may use
├── controller   @RestController: HTTP only — validate input, call one service, return a DTO
├── service      @Service: business logic and transactions
├── repository   Spring Data JPA interfaces (entities)
├── dao          @Repository classes with SQL (JdbcClient): reports, dashboards, lookups, read models
├── dto          request and response records (what the API sends and receives)
├── entity       JPA entities (tables), private fields with getters and setters
├── model        enums and domain types
├── util         pure helpers and business-rule classes (unit-tested without Spring)
├── client       calls to other systems (S3, Microsoft Graph mail, the search service)
└── config       Spring configuration
```

Dependencies only point downwards: `controller → service → repository / dao → entity`, with `dto`, `model` and
`util` shared. Controllers never touch repositories, entities or SQL; SQL lives only in DAOs; modules use each other only
through `api` (Spring Modulith, `@NamedInterface("api")`). `LayeredArchitectureTest` and `ModularityTests` fail the build if
any of this is broken.

| Module | Layers present (classes) |
|---|---|
| `shared` | controller (1), dto (1), entity (2), model (1), util (5), config (1), exception (3), security (3) |
| `identity` | api (6), controller (4), service (8), repository (2), dao (2), dto (16), entity (2), model (1), util (3), config (2) |
| `registry` | api (3), controller (1), service (3), repository (3), dto (5), entity (3), util (1) |
| `catalogue` | api (3), controller (1), service (1), repository (2), dto (5), entity (2) |
| `workflow` | api (4), controller (1), service (1), repository (2), dto (3), entity (2), util (1) |
| `documents` | api (5), controller (2), service (4), repository (4), dao (1), dto (9), entity (4), util (3), client (2) |
| `matching` | api (2), controller (1), service (1), repository (4), dto (6), entity (4), util (1), client (1) |
| `giving` | api (2), controller (1), service (2), repository (3), dto (4), entity (3), util (1) |
| `notifications` | service (1), client (3) |
| `reporting` | controller (6), service (6), dao (8), dto (25), util (2) |
| `importer` | controller (1), service (1), repository (2), dao (1), dto (2), entity (2), model (1), util (2) |
| `audit` | controller (2), service (5), dao (2), dto (6), entity (2), model (2), util (4), config (1) |
| `referencedata` | controller (2), service (2), dao (2), dto (5) |

### What each module owns

| Module | Owns | Key rules |
|---|---|---|
| `shared` | ids (`RefNo`), `Periodicity`, errors (RFC 9457), base entity, caller identity, field encryption | Legacy reference numbers kept; bank account numbers AES-GCM encrypted at rest |
| `identity` | users, sessions, invitation/reset tokens, theme, admin users, roles and permissions, first-admin bootstrap | No temporary passwords; tokens hashed and single-use; 12+ character passwords; lock after 5 failures |
| `workflow` | one maker-checker engine for every record type | Checker ≠ maker; reject needs a reason; editing an approved record sends it back |
| `registry` | organisations, contacts, bank accounts | Verified ⇔ every mandatory document for that organisation type is reviewed |
| `catalogue` | purposes and needs (causes + projects) | A need completes when money raised reaches its target |
| `documents` | uploads, review, share requests, signed downloads, document types and mandatory rules, legacy file copy | Donors see shareable documents only after the owner approves; links last 5 minutes |
| `matching` | engine runs, candidates, review, confirmed matches, suggested donors | Scores keyed by id; every candidate decided; priorities 1..n; decisions become training labels |
| `giving` | donations, recurring plans, instalments, reminders | Idempotency-Key; paid vs pledged kept separate |
| `notifications` | email through Microsoft Graph (or the log in development) | Sent after commit, retried |
| `reporting` | role dashboards, chart details, the original beneficiary reports (incl. customize report), lookups for form pickers, side-panel counters, CSV exports | `/api/v2/dashboards/me` returns chart-ready JSON; `/api/v2/reports/*.csv` are fixed, parameterised queries (no user SQL); CSV cells are protected against formula injection |
| `audit` | who did what, when, from where | Every user action in the append-only `audit_event` (in the same transaction as the change); Envers revisions stamped with the user and request id; record trails, page activity and the audit log (`/api/v2/audit/*`); `/api/v2/history/{type}/{id}` for field changes |
| `importer` | bulk Excel import with templates | All-or-nothing; every problem reported with row and column |
| `referencedata` | sectors, states, periodicities, document types | Public lookups; super admins add sectors and switch them off (sectors already used are never renamed) |

## Migrating from the old system

1. Restore the old PostgreSQL dump into a schema called `legacy` in the new database.
2. Copy the old `/uploads` folder to the server and set `LEGACY_UPLOAD_DIR` to it.
3. Start the app once with `SPRING_PROFILES_ACTIVE=legacy-migration`. Flyway runs V100, then `LegacyFileCopier` copies
   the files into object storage (safe to re-run; missing files are logged).
4. Reconcile: `select * from legacy_migration_report;` compares old and new counts and the total paid amount, and lists
   how each old benefactor type was mapped (detected from its name: "Corporate Donor" → corporate, otherwise individual).

What the migration decides: old reference numbers are kept; only real bcrypt hashes are copied (people whose old
password was an unhashed temporary one use "Forgot password"); `paid_amount` is the old `committed_amount` for every
gift (the old `amount` meant different things for matched and direct gifts); raised amounts are recomputed from
donations; old status codes become approval requests.

## Tests and what was verified

| Check | Where it runs | Result in this build |
|---|---|---|
| 108 backend unit tests on Java 17 (incl. 6 architecture rules and 7 TLS tests with real handshakes): approvals, priorities, donation arithmetic, ₹ grouping, financial-year cycles and quarters, document access, uploads, verification, history diffs, import validation, password policy, field encryption, dashboard groups, CSV safety, reference codes, mandatory-rule changes, chart detail queries (scoping, allow-listing, periods), beneficiary report queries (columns, joins, financial years, totals), role rules and the built-in role seed, the layering rules (`LayeredArchitectureTest`), TLS trust and the transport policy, audit action names, trail merging and visibility, and the rule that every state-changing endpoint is audited, deployment settings, links in trial emails | `mvn test` | the real test files were compiled with javac 17 and run on Java 17 here: 108 / 108 pass |
| Spring Modulith boundary test, Excel template round trip, 7 HTTP integration tests (sessions, CSRF, permissions, themes, admin users, imports, history) 3 dashboard SQL tests, 3 reference-data/report tests 4 chart-detail/lookup/counter tests 4 beneficiary-report tests, 3 roles-and-permissions tests, 3 audit-trail tests, 2 staff-account tests and 3 sign-in tests (the real sign-in, starting without a session) (field changes, visibility, refused and invalid attempts, append-only table) against PostgreSQL in Docker | `mvn verify` (needs Docker) | written; not runnable here |
| All Java files parse; every internal import resolves; no module uses another module's internals; no dependency cycles between modules; every API call the screens make has a matching endpoint; every response type the screens use matches its Java record field for field (47 types) | — | 0 errors |
| Frontend strict type-check; 89 tests incl. end-to-end runs through every screen and role on demo data, report Excel/PDF files and theme contrast, roles and permissions, audit trails and the audit log, the responsive menu, card layout, full-width pages and adding staff | `npm test` | 89 / 89 pass |
| Frontend production build, demo data excluded; layout checked in Chrome at 390, 820, 1280, 1366, 1536, 1920 and 2560 px wide | `npm run build` | passes |
| Search service (ranking, calibration, API contract, layering rules, TLS handshakes) | `pytest` | 21 / 21 pass |
| Deployment scripts (`deploy/deploy.sh`, `deploy/tls/make-certs.sh`): shellcheck, and every command run here (setup, checks, real certificates, and dry runs of up, backup, update, rollback, restore and legacy migration) | `shellcheck` | clean; all commands behave as documented |

**Before the first release, run `mvn -B verify` on a machine with Maven Central and Docker.** Maven was not reachable
when this was written, so the Spring build itself, the 45 Spring tests (42 need Docker; the 3 start-up guard tests do not) and these library versions in
`backend/pom.xml` still need confirming against Spring Boot 4.1: Spring Modulith, springdoc, ShedLock, AWS SDK, Apache POI
5.4.1, and the Testcontainers 2 artifact `testcontainers-postgresql`.

## Decisions still needed from the HumSaadhak team

The production email sender address and the open questions Q1–Q9 in the blueprint. The mandatory documents per
organisation type start from the list in `V2__reference_data.sql` and can now be changed by a super admin under
Reference data, without a release. How long to keep the audit log (suggested: at least as long as financial records, often
8 years, with older years moved to cheaper storage by partitioning `audit_event` by year).
