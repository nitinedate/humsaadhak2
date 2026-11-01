package org.humsaadhak.shared;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Settings the deployment depends on (deploy/compose.yaml, DEPLOYMENT.md). */
class DeploymentConfigTest {
    static String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    @Test
    void releasesAfterTheLegacyMigrationStillApplyTheirMigrations() throws Exception {
        String app = read("src/main/resources/application.yml");
        assertThat(app.contains("out-of-order: true")).isTrue();
        assertThat(read("src/main/resources/application-legacy-migration.yml").contains("classpath:db/legacy")).isTrue();
    }

    @Test
    void selfHostedStorageGetsItsBucketAndEncryptionKey() throws Exception {
        String compose = read("../deploy/compose.yaml");
        assertThat(compose.contains("STORAGE_CREATE_BUCKET: \"true\"")).isTrue();
        assertThat(compose.contains("MINIO_KMS_SECRET_KEY")).isTrue();      // uploads ask for server-side encryption
        assertThat(compose.contains("MAIL_FROM")).isTrue();
        assertThat(read("../deploy/.env.example").contains("STORAGE_KMS_KEY=")).isTrue();
    }

    @Test
    void theDatabaseIsLocalOrManagedAndAlwaysVerified() throws Exception {
        String compose = read("../deploy/compose.yaml");
        assertThat(compose.contains("profiles: [local-db]")).isTrue();                   // the local container is optional
        assertThat(compose.contains("jdbc:postgresql://${DB_HOST:-db}:${DB_PORT:-5432}/${DB_NAME:-humsaadhak}?sslmode=verify-full")).isTrue();
        assertThat(compose.contains("@${DB_HOST:-db}:${DB_PORT:-5432}/${DB_NAME:-humsaadhak}?sslmode=verify-full")).isTrue();
        assertThat(compose.contains("required: false")).isTrue();
        assertThat(read("../deploy/.env.example").contains("COMPOSE_PROFILES=local-db")).isTrue();
        String prod = read("../deploy/.env.prod.example");
        assertThat(prod.contains("COMPOSE_PROFILES=\n")).isTrue();
        assertThat(prod.contains("DB_CA_FILE=./tls/digitalocean-ca.crt")).isTrue();
        assertThat(prod.contains("DB_PASSWORD=\n")).isTrue();                            // templates hold no secrets
    }

    @Test
    void javaIsAmazonCorretto17EverywhereWithUtf8() throws Exception {
        assertThat(read("pom.xml").contains("<java.version>17</java.version>")).isTrue();
        String docker = read("../Dockerfile");
        assertThat(docker.contains("FROM maven:3.9-amazoncorretto-17 AS jar")).isTrue();
        assertThat(docker.contains("FROM amazoncorretto:17-al2023-headless")).isTrue();
        assertThat(docker.contains("-Dfile.encoding=UTF-8")).isTrue();         // Java 17's default charset follows the locale
        assertThat(docker.contains("USER 10001:10001")).isTrue();               // owns its TLS key
        assertThat(docker.contains("temurin")).isFalse();
        assertThat(read("../.github/workflows/ci.yml").contains("distribution: corretto, java-version: '17'")).isTrue();
    }

    @Test
    void theBuildContextLeavesOutLocalFilesSettingsAndKeys() throws Exception {
        String ignore = read("../.dockerignore");
        assertThat(ignore.contains("**/node_modules")).isTrue();     // a Windows node_modules would break the Linux build
        assertThat(ignore.contains("backend/target")).isTrue();
        assertThat(ignore.contains("\ndeploy\n")).isTrue();            // .env files and TLS keys never reach the builder
        assertThat(ignore.contains("backups")).isTrue();
    }

    @Test
    void buildsKeepTheirDownloadsAndRetry() throws Exception {
        String docker = read("../Dockerfile");
        assertThat(docker.contains("--mount=type=cache,target=/root/.npm npm ci --fetch-retries=5")).isTrue();
        assertThat(docker.contains("--mount=type=cache,target=/root/.m2 mvn -q -B -Daether.connector.http.retryHandler.count=5")).isTrue();
        assertThat(read("../deploy/deploy.sh").contains("build_images")).isTrue();       // the build is retried 3 times
        assertThat(read("../deploy/deploy.ps1").contains("Build-Images")).isTrue();
    }

    @Test
    void storageIsBuiltFromMinioSourceAtAVerifiedCommit() throws Exception {
        // MinIO no longer publishes free Docker images (minio/minio on Docker Hub is gone): the image is built from source
        String compose = read("../deploy/compose.yaml");
        assertThat(compose.contains("build: ./minio")).isTrue();
        assertThat(compose.contains("image: minio/minio")).isFalse();
        String minio = read("../deploy/minio/Dockerfile");
        assertThat(minio.contains("ARG MINIO_RELEASE=RELEASE.2025-10-15T17-29-55Z")).isTrue();
        assertThat(minio.contains("ARG MINIO_COMMIT=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a")).isTrue();
        assertThat(minio.contains("test \"$(git -C minio rev-parse HEAD)\" = \"$MINIO_COMMIT\"")).isTrue();
        assertThat(minio.contains("go build -tags kqueue")).isTrue();
    }

    // Windows PowerShell 5.1's built-in aliases. An alias wins over a function of the same name, so a function called
    // Curl ran Invoke-WebRequest instead (deploy_local.cmd failed while waiting for health on Windows).
    static final java.util.Set<String> PS51_ALIASES = java.util.Set.of(("ac asnp cat cd cfs chdir clc clear clhy cli clp cls clv cnsn compare copy cp cpi cpp curl cvpa dbp del diff dir dnsn ebp echo epal epcsv epsn erase etsn exsn fc fhx fl foreach ft fw gal gbp gc gcb gci gcm gcs gdr ghy gi gin gjb gl gm gmo gp gps gpv group gsn gsnp gsv gtz gu gv gwmi h history icm iex ihy ii ipal ipcsv ipmo ipsn irm ise iwmi iwr kill lp ls man md measure mi mount move mp mv nal ndr ni nmo npssc nsn nv ogv oh popd ps pushd pwd r rbp rcjb rcsn rd rdr ren ri rjb rm rmdir rmo rni rnp rp rsn rsnp rujb rv rvpa rwmi sajb sal saps sasv sbp sc scb select set shcm si sl sleep sls sort sp spjb spps spsv start stz sujb sv swmi tee trcm type wget where wjb write").split(" "));

    @Test
    void noPowerShellFunctionIsShadowedByAWindowsPowerShellAlias() throws Exception {
        for (String script : new String[] {"../deploy/deploy.ps1", "../deploy/db.ps1"}) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^function ([A-Za-z0-9-]+)").matcher(read(script));
            int functions = 0;
            while (m.find()) {
                functions++;
                assertThat(PS51_ALIASES.contains(m.group(1).toLowerCase(java.util.Locale.ROOT))).isFalse();
            }
            assertThat(functions > 20).isTrue();
        }
    }

    @Test
    void nativeCallsWithRedirectedErrorsCannotEndAPowerShell51Script() throws Exception {
        // Windows PowerShell 5.1 turns redirected stderr into errors, and the scripts use ErrorActionPreference=Stop:
        // curl's "could not connect" while the proxy started ended deploy_local.cmd. Such calls go through Invoke-Quiet.
        java.util.regex.Pattern call = java.util.regex.Pattern.compile("&\\s*(docker|\\$CurlCmd|\\$exe)[^#\\n]*(2>|\\*>)");
        for (String script : new String[] {"../deploy/deploy.ps1", "../deploy/db.ps1"}) {
            int calls = 0;
            for (String line : read(script).split("\n")) {
                if (call.matcher(line).find()) { calls++; assertThat(line.contains("Invoke-Quiet {")).isTrue(); }
            }
            assertThat(calls > 2).isTrue();
        }
    }

    @Test
    void theProxyStartsWithOrWithoutALetsEncryptEmail() throws Exception {
        // "email" with no address is a Caddyfile error: with an empty ACME_EMAIL the proxy crash-looped (Caddyfile:5)
        String caddy = read("../deploy/Caddyfile");
        assertThat(caddy.contains("email {$ACME_EMAIL")).isFalse();
        assertThat(caddy.contains("{$ACME_EMAIL_LINE}")).isTrue();
        assertThat(read("../deploy/compose.yaml").contains("then export ACME_EMAIL_LINE=\\\"email $$ACME_EMAIL\\\"; fi; exec caddy run")).isTrue();
    }

    @Test
    void theHealthWaitStopsAtACrashingContainer() throws Exception {
        assertThat(read("../deploy/deploy.ps1").contains("Find-CrashLoop")).isTrue();
        assertThat(read("../deploy/deploy.sh").contains("crash_loop")).isTrue();
    }

    @Test
    void pgAdminIsOptionalLocalOnlyAndConnectsWithFullCertificateChecks() throws Exception {
        String compose = read("../deploy/compose.yaml");
        String svc = compose.substring(compose.indexOf("  pgadmin:"), compose.indexOf("\n  app:"));
        assertThat(svc.contains("profiles: [pgadmin]")).isTrue();                          // opt-in
        assertThat(svc.contains("image: dpage/pgadmin4:9.18")).isTrue();                   // pinned
        assertThat(svc.contains("ports: [\"127.0.0.1:${PGADMIN_PORT:-5050}:80\"]")).isTrue(); // never on a public interface
        assertThat(svc.contains("PGADMIN_CONFIG_SERVER_MODE: \"True\"")).isTrue();           // sign-in required
        assertThat(svc.contains("PGADMIN_EMAIL:?")).isFalse();     // Compose reads every service: no ":?" while the profile is off
        String start = read("../deploy/pgadmin/start.sh");
        assertThat(start.contains("\"sslmode\": \"verify-full\", \"sslrootcert\": \"/db-ca.crt\"")).isTrue();
        assertThat(start.contains("pga_user_")).isTrue();          // pgAdmin's storage-folder name, for the CA certificate
        assertThat(read("../deploy/.env.example").contains("COMPOSE_PROFILES=local-db,pgadmin")).isTrue();
        assertThat(read("../deploy/.env.prod.example").contains("COMPOSE_PROFILES=\n")).isTrue();   // off in production
    }

    @Test
    void smtpEmailIsEncryptedVerifiedAndConfiguredOnlyFromTheEnvironment() throws Exception {
        String app = read("src/main/resources/application.yml");
        assertThat(app.contains("\"[mail.smtp.starttls.required]\": true")).isTrue();
        assertThat(app.contains("\"[mail.smtp.ssl.checkserveridentity]\": true")).isTrue();
        assertThat(app.contains("[mail.smtp.ssl.trust]")).isFalse();                 // only by choice, through SMTP_SSL_TRUST
        assertThat(app.contains("smtp-ssl-trust: ${SMTP_SSL_TRUST:}")).isTrue();
        assertThat(app.contains("\"[mail.store.protocol]\": imaps")).isTrue();
        assertThat(app.contains("password: ${SMTP_PASSWORD:}")).isTrue();             // from the environment only (S4)
        assertThat(app.contains("health.mail.enabled: false")).isTrue();              // no sign-in on every health check
        String compose = read("../deploy/compose.yaml");
        for (String k : new String[] {"SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_SSL_TRUST"}) assertThat(compose.contains("      " + k + ": ${" + k)).isTrue();
        assertThat(read("../deploy/.env.example").contains("SMTP_PASSWORD=\n")).isTrue();     // templates hold no secrets
        assertThat(read("../deploy/.env.prod.example").contains("SMTP_PASSWORD=\n")).isTrue();
    }

    @Test
    void everyStartFirstRemovesContainersLeftRenamedByAnInterruptedUpdate() throws Exception {
        // Compose renames a container while replacing it; a leftover ("593b37636000_humsaadhak-search-1") blocks the next start
        String sh = read("../deploy/deploy.sh");
        assertThat(sh.split("compose up -d", -1).length - 1).isEqualTo(1);                      // only inside compose_up
        assertThat(sh.contains("compose_up() { remove_stale_containers; compose up -d \"$@\"; }")).isTrue();
        assertThat(sh.contains("--filter \"label=com.docker.compose.project=$PROJECT\"")).isTrue();  // this project's only
        String ps = read("../deploy/deploy.ps1");
        assertThat(ps.indexOf("Remove-StaleContainers\n  Run-Compose @('up', '-d'") > 0).isTrue();
        assertThat(ps.split("@\\('up', '-d'", -1).length - 1).isEqualTo(1);
        assertThat(ps.contains("--filter \"label=com.docker.compose.project=$Project\"")).isTrue();
    }
}

