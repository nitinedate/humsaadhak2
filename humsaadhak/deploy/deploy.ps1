<#
.SYNOPSIS
  HumSaadhak 2.0 - deployment from Windows. Used by deploy_local.cmd and deploy_prod.cmd (double-click or Command Prompt).

  deploy_local.cmd  = deploy.ps1 [command]        the whole stack on THIS computer (Docker Desktop) with deploy\.env
  deploy_prod.cmd   = deploy.ps1 -Prod [command]  production with deploy\.env.prod:
                        DEPLOY_SSH_HOST set   -> upload the release to that Linux server over SSH and run
                                                 deploy/deploy.sh --prod there (first time: up; later: update, which
                                                 rolls back by itself if the new release is not healthy)
                        DEPLOY_SSH_HOST empty -> run production on this computer (asks first; -Here skips the question)

  Commands: up (default), status, logs [service] [-Follow], down, smoke, help. With a production server, any other
  command is passed to deploy/deploy.sh on the server (backup, rollback, cron, dump-legacy, ...).
  Needs only what Windows 10/11 has: Docker Desktop, PowerShell 5.1+, curl.exe, tar.exe and OpenSSH (ssh, scp).
  The same in bash on a Linux server: deploy/deploy.sh.
#>
param(
  [Parameter(Position = 0)] [string] $Command = 'up',
  [Parameter(Position = 1)] [string] $Service = '',
  [switch] $Prod,
  [switch] $Here,
  [switch] $Follow,
  [switch] $Yes,
  [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$DeployDir = $PSScriptRoot
$RepoDir = Split-Path -Parent $DeployDir
$ParentDir = Split-Path -Parent $RepoDir
$SearchDir = Join-Path $ParentDir 'humsaadhak-search'
$ComposeFile = Join-Path $DeployDir 'compose.yaml'
$TlsDir = Join-Path $DeployDir 'tls'
$Project = 'humsaadhak'
$DbToolsDefault = 'pgvector/pgvector:pg16'
$CertImage = 'alpine:3.20'
if ($Prod) { $EnvFile = Join-Path $DeployDir '.env.prod'; $Example = Join-Path $DeployDir '.env.prod.example'; $Label = 'deploy\.env.prod' }
else       { $EnvFile = Join-Path $DeployDir '.env';      $Example = Join-Path $DeployDir '.env.example';      $Label = 'deploy\.env' }
$Dry = $DryRun.IsPresent -or ($env:DRY_RUN -eq '1')
$Confirmed = $Yes.IsPresent -or ($env:ASSUME_YES -eq '1')
$HealthTimeout = 900; if ($env:HEALTH_TIMEOUT) { $HealthTimeout = [int]$env:HEALTH_TIMEOUT }

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  ok   $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  warn $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "failed: $m" -ForegroundColor Red; exit 1 }

# Windows PowerShell 5.1 turns a native program's redirected stderr into errors, and with ErrorActionPreference=Stop the
# first such line (curl's "could not connect" while the proxy starts, docker's "No such container") ends the script.
# Native programs report failure through their exit code, which the callers check: run them with errors not stopping.
function Invoke-Quiet([scriptblock] $block) {
  $saved = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { & $block } finally { $ErrorActionPreference = $saved }
}

# ---------------------------------------------------------------- settings file
function Read-Setting([string] $key) {
  if (-not (Test-Path -LiteralPath $EnvFile)) { return '' }
  $value = ''
  foreach ($line in Get-Content -LiteralPath $EnvFile) { if ($line -match "^$key=(.*)$") { $value = $Matches[1].TrimEnd("`r") } }
  return $value
}
function Write-Lf([string] $path, [string[]] $lines) {   # LF line endings, no BOM: Docker reads CRLF values with a trailing \r
  [System.IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
}
function Set-Setting([string] $key, [string] $value) {
  $lines = @(Get-Content -LiteralPath $EnvFile); $found = $false
  for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match "^$key=") { $lines[$i] = "$key=$value"; $found = $true } }
  if (-not $found) { $lines += "$key=$value" }
  Write-Lf $EnvFile $lines
}
function Normalize-LineEndings {   # Notepad may save CRLF: Docker would then read every value with a trailing \r
  $raw = [System.IO.File]::ReadAllText($EnvFile)
  if ($raw.Contains("`r")) {
    [System.IO.File]::WriteAllText($EnvFile, ($raw -replace "`r", ''), (New-Object System.Text.UTF8Encoding($false)))
    Ok "$Label had Windows line endings: converted to LF"
  }
}
function Random-Hex([int] $bytes) {
  $b = New-Object byte[] $bytes; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
  return -join ($b | ForEach-Object { $_.ToString('x2') })
}
function Random-Key32 { $b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); return [Convert]::ToBase64String($b) }

function Init-Local {   # deploy\.env for a trial on this computer, as deploy/deploy.sh init would make it
  Copy-Item -LiteralPath $Example -Destination $EnvFile
  Normalize-LineEndings
  $admin = 'admin@example.org'
  if (-not $Confirmed) { $answer = Read-Host "Email of the first super admin [$admin]"; if ($answer) { $admin = $answer } }
  Set-Setting 'DOMAIN' 'localhost'; Set-Setting 'FILES_DOMAIN' 'files.localhost'; Set-Setting 'HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL' $admin
  Set-Setting 'DB_PASSWORD' (Random-Hex 24); Set-Setting 'SEARCH_TOKEN' (Random-Hex 32)
  Set-Setting 'STORAGE_ACCESS_KEY' ('hs' + (Random-Hex 10)); Set-Setting 'STORAGE_SECRET_KEY' (Random-Hex 32)
  Set-Setting 'STORAGE_KMS_KEY' (Random-Key32); Set-Setting 'HUMSAADHAK_FIELD_KEY' (Random-Key32)
  Set-Setting 'MAIL_PROVIDER' 'log'; Set-Setting 'MAIL_LOG_LINKS' 'true'
  Set-Setting 'PGADMIN_EMAIL' $admin; Set-Setting 'PGADMIN_PASSWORD' (Random-Hex 12); Set-Setting 'COMPOSE_PROFILES' 'local-db,pgadmin'
  Ok "created $Label with random secrets (trial on https://localhost; emails are written to the log)"
}

function Db-Host { $h = Read-Setting 'DB_HOST'; if ($h) { return $h } return 'db' }
function External-Db { return ((Db-Host) -ne 'db') }
function Resolve-DeployPath([string] $p) { if ([System.IO.Path]::IsPathRooted($p)) { return $p } return (Join-Path $DeployDir ($p -replace '^\./', '')) }
function Db-CaFile { $f = Read-Setting 'DB_CA_FILE'; if (-not $f) { $f = './tls/out/ca.crt' } return (Resolve-DeployPath $f) }
function Is-Local([string] $d) { return ($d -eq 'localhost' -or $d -like '*.localhost') }

function Has-Profile([string] $name) { return ((',' + (Read-Setting 'COMPOSE_PROFILES') + ',') -like "*,$name,*") }
function PgAdmin-Port { $p = Read-Setting 'PGADMIN_PORT'; if ($p) { return $p } return '5050' }

# deploy\.env files made before pgAdmin was added: add it (local only), with its own sign-in
function Add-PgAdmin {
  if ($Prod -or (Read-Setting 'PGADMIN_PASSWORD')) { return }
  $email = Read-Setting 'PGADMIN_EMAIL'; if (-not $email) { $email = Read-Setting 'HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL' }
  if (-not $email) { return }
  Set-Setting 'PGADMIN_EMAIL' $email; Set-Setting 'PGADMIN_PASSWORD' (Random-Hex 12)
  if (-not (Has-Profile 'pgadmin')) {
    $profiles = Read-Setting 'COMPOSE_PROFILES'; if ($profiles) { $profiles += ',pgadmin' } else { $profiles = 'pgadmin' }
    Set-Setting 'COMPOSE_PROFILES' $profiles
  }
  Ok "pgAdmin added to $Label (sign-in $email; password in PGADMIN_PASSWORD)"
}

# MAIL_PROVIDER=smtp: everything the mail server needs, and what Microsoft 365 would refuse
function Check-Smtp {
  if ((Read-Setting 'MAIL_PROVIDER') -ne 'smtp') { return }
  $missing = @(); foreach ($k in @('SMTP_HOST', 'SMTP_USERNAME', 'SMTP_PASSWORD')) { if (-not (Read-Setting $k)) { $missing += $k } }
  if ($missing.Count) { Fail "MAIL_PROVIDER=smtp needs $($missing -join ', ') in $Label" }
  $pw = Read-Setting 'SMTP_PASSWORD'
  if ($pw.Contains('$') -and -not ($pw.StartsWith("'") -and $pw.EndsWith("'"))) {
    Fail "SMTP_PASSWORD contains a `$, which Docker Compose would replace: write it in single quotes, SMTP_PASSWORD='...'"
  }
  $from = Read-Setting 'MAIL_FROM'; $user = Read-Setting 'SMTP_USERNAME'
  if ($from -and $from.ToLower() -ne $user.ToLower()) {
    Warn "MAIL_FROM ($from) is not SMTP_USERNAME ($user): Microsoft 365 refuses it unless $user may 'Send As' $from"
  }
  if (Read-Setting 'SMTP_SSL_TRUST') {
    Warn "SMTP_SSL_TRUST=$(Read-Setting 'SMTP_SSL_TRUST'): that mail server's certificate is not verified (encrypted, but a forged certificate would be accepted)"
  }
  if ((Read-Setting 'SMTP_HOST') -like '*office365.com') {
    Warn 'Microsoft 365 turns off password sign-in for SMTP (SMTP AUTH Basic) by default at the end of December 2026: move to MAIL_PROVIDER=graph before then (DEPLOYMENT.md, step 4)'
  }
  Ok "email through $(Read-Setting 'SMTP_HOST'):$(if (Read-Setting 'SMTP_PORT') { Read-Setting 'SMTP_PORT' } else { '587' }) as $user (STARTTLS required)"
}

# What a mail server's refusal means, in plain words (Microsoft 365 codes)
function Explain-SmtpError([string]$Message) {
  if ($Message -match '5\.7\.139') { return 'password sign-in for SMTP is turned off for this mailbox or organisation, or blocked by security defaults or Conditional Access: turn on Authenticated SMTP for the mailbox (DEPLOYMENT.md, step 4), or use MAIL_PROVIDER=graph' }
  if ($Message -match '5\.7\.3|5\.7\.57|Authentication unsuccessful|not authenticated|535') { return 'the mail server refused the user name or password: check SMTP_USERNAME and SMTP_PASSWORD (try them at https://outlook.office.com). An account with multi-factor sign-in needs an app password, and Authenticated SMTP must be on for the mailbox' }
  if ($Message -match '5\.7\.60|SendAsDenied') { return "$(Read-Setting 'SMTP_USERNAME') may not send as $(Read-Setting 'MAIL_FROM'): set MAIL_FROM to SMTP_USERNAME, or give it Send As permission" }
  if ($Message -match 'timed out|Unable to connect|could not be resolved|No such host|Name or service not known|actively refused') { return 'the mail server could not be reached: check SMTP_HOST and SMTP_PORT, and that this network allows outgoing connections on that port' }
  return 'the test email was not sent (the mail server''s answer is above)'
}

# smtp-test: sign in to the mail server with the settings file and send one test email, without the app
function Cmd-SmtpTest {
  if (-not (Test-Path -LiteralPath $EnvFile)) { Fail "$Label is missing: run deploy_local.cmd first" }
  if ((Read-Setting 'MAIL_PROVIDER') -ne 'smtp') { Fail "MAIL_PROVIDER is not smtp in $Label" }
  Check-Smtp
  $smtpHost = Read-Setting 'SMTP_HOST'; $user = Read-Setting 'SMTP_USERNAME'
  $port = Read-Setting 'SMTP_PORT'; if (-not $port) { $port = '587' }
  $from = Read-Setting 'MAIL_FROM'; if (-not $from) { $from = $user }
  $to = Read-Setting 'HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL'; if (-not $to) { $to = $user }
  $pw = Read-Setting 'SMTP_PASSWORD'
  if ($pw.Length -ge 2 -and $pw.StartsWith("'") -and $pw.EndsWith("'")) { $pw = $pw.Substring(1, $pw.Length - 2) }
  Info "Signing in to ${smtpHost}:$port as $user (STARTTLS) and sending a test email to $to"
  $credential = New-Object System.Management.Automation.PSCredential($user, (ConvertTo-SecureString $pw -AsPlainText -Force))
  try {
    Send-MailMessage -SmtpServer $smtpHost -Port ([int]$port) -UseSsl -Credential $credential -From $from -To $to `
      -Subject 'HumSaadhak: test email' -Body 'A test email from smtp-test: the mail settings work.' -ErrorAction Stop -WarningAction SilentlyContinue
  } catch {
    $answer = $_.Exception.Message
    if ($_.Exception.InnerException) { $answer += ' ' + $_.Exception.InnerException.Message }
    Write-Host "  the mail server said: $answer"
    Fail (Explain-SmtpError $answer)
  }
  Ok "test email sent to ${to}: the mail settings work"
}

function Check-Config {
  if (-not (Test-Path -LiteralPath $EnvFile)) {
    if ($Prod) { Fail "$Label is missing: copy it from humsaadhak-env-files.zip (or make it from deploy\.env.prod.example)" }
    Info "No $Label yet: creating it for a trial on this computer"; Init-Local
  }
  Normalize-LineEndings
  Add-PgAdmin
  $missing = @()
  foreach ($k in @('DOMAIN', 'FILES_DOMAIN', 'DB_PASSWORD', 'SEARCH_TOKEN', 'STORAGE_ACCESS_KEY', 'STORAGE_SECRET_KEY', 'STORAGE_KMS_KEY', 'HUMSAADHAK_FIELD_KEY')) {
    if (-not (Read-Setting $k)) { $missing += $k }
  }
  if ($missing.Count) { Fail "$Label has no value for: $($missing -join ', ')" }
  foreach ($k in @('STORAGE_KMS_KEY', 'HUMSAADHAK_FIELD_KEY')) {
    $n = 0; try { $n = [Convert]::FromBase64String((Read-Setting $k)).Length } catch { $n = -1 }
    if ($n -ne 32) { Fail "$k must be 32 random bytes in base64; it decodes to $n bytes" }
  }
  $dbName = Read-Setting 'DB_NAME'; if (-not $dbName) { $dbName = 'humsaadhak' }
  if ($dbName -notmatch '^[a-z_][a-z0-9_]*$') { Fail 'DB_NAME may contain only lowercase letters, digits and _' }
  if ((Read-Setting 'DB_PASSWORD') -notmatch '^[A-Za-z0-9._~-]+$') { Fail 'DB_PASSWORD may contain only letters, digits and . _ ~ - (it is part of a connection URL)' }
  $profiles = ',' + (Read-Setting 'COMPOSE_PROFILES') + ','
  if (External-Db) {
    if (-not (Test-Path -LiteralPath (Db-CaFile))) { Fail "DB_CA_FILE $(Db-CaFile) is missing: download the CA certificate from the database provider (DEPLOYMENT.md, Managed database)" }
    if ($profiles -like '*,local-db,*') { Fail "COMPOSE_PROFILES contains local-db, but DB_HOST is $(Db-Host): remove local-db" }
    Ok "managed database $(Read-Setting 'DB_USER')@$(Db-Host):$(Read-Setting 'DB_PORT')/$dbName"
  } elseif ($profiles -notlike '*,local-db,*') { Fail "the local database needs COMPOSE_PROFILES=local-db in $Label (or set DB_HOST for a managed database)" }
  if (Has-Profile 'pgadmin') {
    $pe = Read-Setting 'PGADMIN_EMAIL'
    if ($pe -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$' -or $pe -match '\.(localhost|local|test|invalid|example)$') {
      Fail "PGADMIN_EMAIL must be a real email address (pgAdmin refuses '$pe'); it is only used to sign in to pgAdmin"
    }
    if ((Read-Setting 'PGADMIN_PASSWORD').Length -lt 8) { Fail 'PGADMIN_PASSWORD must have at least 8 characters' }
  }
  Check-Smtp
  $domain = Read-Setting 'DOMAIN'
  if (-not (Is-Local $domain)) {
    if ((Read-Setting 'MAIL_LOG_LINKS') -eq 'true') { Fail 'MAIL_LOG_LINKS=true writes sign-in links to the log: only for localhost trials' }
    if ((Read-Setting 'MAIL_PROVIDER') -notin @('graph', 'smtp')) { Warn 'MAIL_PROVIDER is neither graph nor smtp: no email will reach anyone' }
    if (Read-Setting 'MAIL_REDIRECT_TO') { Warn "MAIL_REDIRECT_TO is set: every email goes to $(Read-Setting 'MAIL_REDIRECT_TO') (testing only)" }
  }
  Ok "settings complete: $Label, https://$domain"
}

# ---------------------------------------------------------------- docker, compose, curl (no function is called docker or curl)
function Run-Docker([string[]] $arguments) {
  if ($Dry) { Write-Host ('  [dry-run] docker ' + ($arguments -join ' ')); return }
  & docker @arguments
  if ($LASTEXITCODE -ne 0) { Fail ('docker ' + ($arguments[0..1] -join ' ') + " failed (exit $LASTEXITCODE)") }
}
function Run-Compose([string[]] $arguments) {
  $env:COMPOSE_PROFILES = Read-Setting 'COMPOSE_PROFILES'
  try { Run-Docker (@('compose', '--project-name', $Project, '--env-file', $EnvFile, '-f', $ComposeFile) + $arguments) }
  finally { Remove-Item Env:COMPOSE_PROFILES -ErrorAction SilentlyContinue }
}
# A container left renamed by an interrupted update (for example "593b37636000_humsaadhak-search-1"): Compose renames
# the old container while it replaces it, and a leftover blocks the next attempt ("The container name ... is already in
# use"). Only this project's containers with that temporary name are removed; they were being replaced anyway.
function Remove-StaleContainers {
  if ($Dry) { return }
  try { $rows = Invoke-Quiet { & docker ps -a --filter "label=com.docker.compose.project=$Project" --format '{{.ID}} {{.Names}}' 2> $null } }
  catch { return }
  foreach ($row in @($rows)) {
    $id, $name = "$row".Trim().Split(' ', 2)
    if ($name -match '^[0-9a-f]{12}_') {
      Warn "removing $name, left behind by an interrupted update (it would block the new container)"
      Run-Docker @('rm', '-f', $id) | Out-Null
    }
  }
}
function Need-Docker {
  if ($Dry) { return }
  if (-not (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) { Fail 'Docker is not installed: install Docker Desktop (https://www.docker.com/products/docker-desktop)' }
  Invoke-Quiet { & docker info *> $null }
  if ($LASTEXITCODE -ne 0) { Fail 'Docker Desktop is not running: start it, wait until it says "Engine running", and try again' }
  $v = ("" + (Invoke-Quiet { & docker compose version --short 2> $null })).Trim().TrimStart('v')
  if (-not $v) { Fail 'Docker Compose v2 is missing: update Docker Desktop' }
  if ([version]($v -replace '[^0-9.].*$', '') -lt [version]'2.20.0') { Fail "Docker Compose $v is too old: 2.20 or newer is needed (update Docker Desktop)" }
  Ok "Docker Desktop with Compose $v"
}
# The first match only: Windows has curl.exe in System32, and Git for Windows may add another one
$CurlCmd = Get-Command curl.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $CurlCmd) { $CurlCmd = Get-Command curl -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1 }
# Not named Curl: in Windows PowerShell 5.1 'curl' is an alias of Invoke-WebRequest, and aliases win over functions.
function Invoke-CurlExe([string[]] $arguments) {
  if (-not $CurlCmd) { Fail 'curl.exe is missing (it ships with Windows 10 1803 and later)' }
  if (Is-Local (Read-Setting 'DOMAIN')) { $arguments = @('--insecure') + $arguments }   # localhost: Caddy's own CA
  $out = Invoke-Quiet { & $CurlCmd.Source @arguments 2> $null }
  return ("" + ($out -join "`n"))
}

# ---------------------------------------------------------------- certificates (made in a container: no bash or openssl on Windows)
function Ensure-Certs {
  $crt = Join-Path $TlsDir 'out\app\tls.crt'
  if (Test-Path -LiteralPath $crt) {
    $days = [int]((New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($crt)).NotAfter - (Get-Date)).TotalDays
    if ($days -gt 30) { Ok "internal certificates valid for $days more days"; return }
    Warn "internal certificates expire in $days days: renewing"
  }
  Info 'Making the internal certificates (deploy\tls\out; the CA is kept if it exists)'
  Run-Docker @('run', '--rm', '-v', "${TlsDir}:/work", $CertImage, 'sh', '-c', 'apk add --no-cache openssl > /dev/null && sh /work/make-certs.sh')
  Ok 'internal certificates made; keep deploy\tls\out\ca.key safe'
}

# ---------------------------------------------------------------- a managed database (production on this computer)
function Db-Psql([string] $database, [string] $sql) {
  $image = Read-Setting 'DB_TOOLS_IMAGE'; if (-not $image) { $image = $DbToolsDefault }
  $port = Read-Setting 'DB_PORT'; if (-not $port) { $port = '5432' }
  $conn = "host=$(Db-Host) port=$port dbname=$database user=$(Read-Setting 'DB_USER') sslmode=verify-full sslrootcert=/db-ca.crt"
  if ($Dry) { Write-Host "  [dry-run] psql $database on $(Db-Host):$port (sslmode=verify-full): $sql"; return '' }
  $env:PGPASSWORD = Read-Setting 'DB_PASSWORD'
  try { $out = Invoke-Quiet { & docker run --rm -e PGPASSWORD -v "$(Db-CaFile):/db-ca.crt:ro" $image psql -d $conn -v ON_ERROR_STOP=1 -At -c $sql 2>&1 } }
  finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
  if ($LASTEXITCODE -ne 0) { Fail "cannot use the database on $(Db-Host): $out. Check DB_* and DB_CA_FILE in $Label and the cluster's trusted sources" }
  return ("" + ($out -join "`n")).Trim()
}
function Ensure-Database {
  if (-not (External-Db)) { return }
  $name = Read-Setting 'DB_NAME'; if (-not $name) { $name = 'humsaadhak' }
  $admin = Read-Setting 'DB_ADMIN_DB'; if (-not $admin) { $admin = 'postgres' }
  Info "Database $name on $(Db-Host)"
  if ((Db-Psql $admin "select 1 from pg_database where datname = '$name'") -eq '1') { Ok "database $name exists" }
  elseif (-not $Dry) { Db-Psql $admin "create database $name" | Out-Null; Ok "created database $name" }
  if ((Db-Psql $name "select (to_regclass('public.benefactor') is not null and to_regclass('public.flyway_schema_history') is null)::int") -eq '1') {
    Fail "DB_NAME=$name is the old system's database. Use a new database (e.g. humsaadhak) and migrate the old one with deploy/deploy.sh migrate-legacy"
  }
}

# ---------------------------------------------------------------- up on this computer
# A container that keeps crashing will never become healthy: stop waiting and show its log (Docker restarts it each time)
function Find-CrashLoop {
  $env:COMPOSE_PROFILES = Read-Setting 'COMPOSE_PROFILES'
  try { $rows = Invoke-Quiet { & docker compose --project-name $Project --env-file $EnvFile -f $ComposeFile ps -a --format '{{.Name}} {{.Service}}' 2> $null } }
  finally { Remove-Item Env:COMPOSE_PROFILES -ErrorAction SilentlyContinue }
  foreach ($row in @($rows | Where-Object { $_ })) {
    $name, $service = "$row".Trim() -split ' ', 2
    $restarts = ("" + (Invoke-Quiet { & docker inspect -f '{{.RestartCount}}' $name 2> $null })).Trim()
    if ($restarts -match '^\d+$' -and [int]$restarts -ge 3) { return @($service, [int]$restarts) }
  }
  return $null
}

function Wait-Healthy {
  if ($Dry) { return }
  $url = "https://$(Read-Setting 'DOMAIN')/actuator/health/readiness"
  Info "Waiting for $url (up to $HealthTimeout s; the first start also loads the search models)"
  $start = Get-Date; $lastReport = 0
  while (((Get-Date) - $start).TotalSeconds -lt $HealthTimeout) {
    if ((Invoke-CurlExe @('-fsS', '--max-time', '10', $url)) -like '*"UP"*') { Ok ("healthy after {0:N0} s" -f ((Get-Date) - $start).TotalSeconds); return }
    $elapsed = [int]((Get-Date) - $start).TotalSeconds
    $crash = Find-CrashLoop
    if ($crash) {
      Run-Compose @('logs', '--tail', '30', $crash[0])
      Fail ("the {0} container keeps crashing (restarted {1} times), so the site cannot start. Its log is above; send it along if the cause is not clear" -f $crash[0], $crash[1])
    }
    if ($elapsed - $lastReport -ge 60) {
      $lastReport = $elapsed
      $env:COMPOSE_PROFILES = Read-Setting 'COMPOSE_PROFILES'
      try { $states = Invoke-Quiet { & docker compose --project-name $Project --env-file $EnvFile -f $ComposeFile ps --format '{{.Service}}={{.State}}' 2> $null } }
      finally { Remove-Item Env:COMPOSE_PROFILES -ErrorAction SilentlyContinue }
      Write-Host ("  ... {0} s, still starting: {1}" -f $elapsed, (($states | Where-Object { $_ }) -join ' '))
    }
    Start-Sleep -Seconds 10
  }
  Run-Compose @('ps'); Run-Compose @('logs', '--tail', '40', 'proxy'); Run-Compose @('logs', '--tail', '60', 'app')
  Fail "the app did not become healthy within $HealthTimeout s"
}
function Cmd-Smoke {
  if ($Dry) { return }
  $d = Read-Setting 'DOMAIN'; $f = Read-Setting 'FILES_DOMAIN'; $fails = 0
  Info 'Smoke tests'
  if ((Invoke-CurlExe @('-s', '-o', 'NUL', '-w', '%{http_code}', '--max-time', '15', "https://$d/")) -match '200') { Ok "https://$d/ serves the app" } else { Warn "https://$d/ did not answer 200"; $fails++ }
  $code = Invoke-CurlExe @('-s', '-o', 'NUL', '-w', '%{http_code}', '--max-time', '15', "http://$d/")
  if ($code -match '^30[178]') { Ok "http:// redirects to https:// ($code)" } else { Warn "http://$d/ answered $code, expected a redirect"; $fails++ }
  $headers = Invoke-CurlExe @('-sS', '-D', '-', '-o', 'NUL', '--max-time', '15', "https://$d/")
  if ($headers -match '(?im)^strict-transport-security:') { Ok 'HSTS header present' } else { Warn 'no Strict-Transport-Security header'; $fails++ }
  if ($headers -match '(?im)^content-security-policy:') { Ok 'Content-Security-Policy header present' } else { Warn 'no Content-Security-Policy header'; $fails++ }
  if ((Invoke-CurlExe @('-s', '-o', 'NUL', '-w', '%{http_code}', '--max-time', '15', "https://$f/minio/health/live")) -match '200') { Ok "https://$f/ (document downloads) is reachable" } else { Warn "https://$f/ is not reachable"; $fails++ }
  if ($fails) { Fail "$fails smoke test(s) failed" } else { Ok 'all smoke tests passed' }
}
# The first build downloads about 3 GB, so a slow or dropped connection is likely somewhere: retry the build. The
# Dockerfiles keep finished downloads in build caches, so a retry only fetches what is still missing.
function Build-Images {
  if ($Dry) { Run-Compose @('build', '--pull'); return }
  for ($attempt = 1; $attempt -le 3; $attempt++) {
    $env:COMPOSE_PROFILES = Read-Setting 'COMPOSE_PROFILES'
    try { Invoke-Quiet { & docker compose --project-name $Project --env-file $EnvFile -f $ComposeFile build --pull } }
    finally { Remove-Item Env:COMPOSE_PROFILES -ErrorAction SilentlyContinue }
    if ($LASTEXITCODE -eq 0) { Ok 'images built'; return }
    if ($attempt -lt 3) {
      Warn "the build failed (exit $LASTEXITCODE), most often a download that timed out. Retrying ($($attempt + 1) of 3); finished downloads are kept"
      Start-Sleep -Seconds 15
    }
  }
  Fail 'the build failed 3 times. If the log above shows a timeout, check the internet connection and run this again: finished downloads are kept'
}

function Cmd-Up {
  Need-Docker
  if (-not (Test-Path -LiteralPath $SearchDir)) { Fail "the search service is missing: expected $SearchDir next to $RepoDir" }
  Check-Config
  Ensure-Certs
  Ensure-Database
  Info 'Building images (the first build downloads about 3 GB: Maven, npm and the search models; 15 to 40 minutes)'
  Build-Images
  Info 'Starting HumSaadhak'
  Remove-StaleContainers
  Run-Compose @('up', '-d', '--remove-orphans')
  Wait-Healthy
  Cmd-Smoke
  Write-Host ''
  Write-Host "HumSaadhak is running at https://$(Read-Setting 'DOMAIN')" -ForegroundColor Green
  $admin = Read-Setting 'HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL'
  if ($admin) {
    if ((Read-Setting 'MAIL_PROVIDER') -eq 'log' -and (Read-Setting 'MAIL_LOG_LINKS') -eq 'true') {
      Write-Host "  First sign-in: the set-password link for $admin is in the log:  deploy_local.cmd logs app   (look for 'Mail link')"
    } elseif (Read-Setting 'MAIL_REDIRECT_TO') { Write-Host "  First sign-in: $admin's set-password email goes to $(Read-Setting 'MAIL_REDIRECT_TO') (every email is redirected there)" }
    else { Write-Host "  First sign-in: $admin receives a set-password email (on an empty database)" }
  }
  if (Is-Local (Read-Setting 'DOMAIN')) { Write-Host '  The browser warns about the certificate on https://localhost: Caddy made it locally; continue to the site.' }
  if (Has-Profile 'pgadmin') { Show-PgAdmin }
}

# ---------------------------------------------------------------- production on a Linux server over SSH
function Need-Tool([string] $name, [string] $hint) {
  if (-not (Get-Command $name -CommandType Application -ErrorAction SilentlyContinue)) { Fail "$name is missing. $hint" }
}
function Ssh-Args {
  $a = @('-p', $(if (Read-Setting 'DEPLOY_SSH_PORT') { Read-Setting 'DEPLOY_SSH_PORT' } else { '22' }))
  $key = Read-Setting 'DEPLOY_SSH_KEY'; if ($key) { $a += @('-i', (Resolve-DeployPath $key)) }
  return $a
}
function Target { $u = Read-Setting 'DEPLOY_SSH_USER'; if (-not $u) { $u = 'root' }; return "$u@$(Read-Setting 'DEPLOY_SSH_HOST')" }
function Remote-Dir { $d = Read-Setting 'DEPLOY_DIR'; if ($d) { return $d } return '/opt/humsaadhak-2.0' }
function Run-Native([string] $exe, [string[]] $arguments) {
  if ($Dry) { Write-Host "  [dry-run] $exe $($arguments -join ' ')"; return }
  & $exe @arguments
  if ($LASTEXITCODE -ne 0) { Fail "$exe failed (exit $LASTEXITCODE)" }
}
function Remote-Run([string] $script) {   # a POSIX sh script, uploaded with LF line endings and run with a terminal
  $file = Join-Path ([System.IO.Path]::GetTempPath()) 'humsaadhak-remote.sh'
  Write-Lf $file @('set -e', $script, 'rm -f /tmp/humsaadhak-remote.sh')
  $scpPort = @(); $sa = Ssh-Args; $scpPort = @('-P', $sa[1]); if ($sa.Count -gt 2) { $scpPort += $sa[2..3] }
  Run-Native 'scp' ($scpPort + @('-q', $file, "$(Target):/tmp/humsaadhak-remote.sh"))
  Run-Native 'ssh' ((Ssh-Args) + @('-t', (Target), 'sh /tmp/humsaadhak-remote.sh'))
  if (-not $Dry) { Remove-Item -LiteralPath $file -ErrorAction SilentlyContinue }
}
function Cmd-RemoteUp {
  Need-Tool 'ssh' 'Windows: Settings > Apps > Optional features > OpenSSH Client'
  Need-Tool 'scp' 'Windows: Settings > Apps > Optional features > OpenSSH Client'
  Need-Tool 'tar' 'tar.exe ships with Windows 10 1803 and later'
  if (-not (Test-Path -LiteralPath $SearchDir)) { Fail "the search service is missing: expected $SearchDir next to $RepoDir" }
  Check-Config
  $dir = Remote-Dir
  Info "Packing the release (humsaadhak and humsaadhak-search, with $Label; without build output, local settings and backups)"
  $archive = Join-Path ([System.IO.Path]::GetTempPath()) 'humsaadhak-release.tgz'
  $exclude = @('node_modules', 'dist', 'dist-preview', 'target', 'backups', 'out', '.env', '.env.db', '__pycache__', '.pytest_cache', '.git', 'tsconfig.tsbuildinfo')
  $tarArgs = @('-czf', $archive) + ($exclude | ForEach-Object { "--exclude=$_" }) + @('-C', $ParentDir, 'humsaadhak', 'humsaadhak-search')
  Run-Native 'tar' $tarArgs
  if (-not $Dry) { Ok ("release packed: {0:N1} MB" -f ((Get-Item $archive).Length / 1MB)) }
  Info "Uploading to $(Target):$dir"
  $sa = Ssh-Args; $scpArgs = @('-P', $sa[1]); if ($sa.Count -gt 2) { $scpArgs += $sa[2..3] }
  Run-Native 'scp' ($scpArgs + @($archive, "$(Target):/tmp/humsaadhak-release.tgz"))
  if (-not $Dry) { Remove-Item -LiteralPath $archive -ErrorAction SilentlyContinue }
  Info 'Deploying on the server (first time: deploy.sh --prod up; afterwards: --prod update with automatic rollback)'
  Remote-Run @"
DIR='$dir'
mkdir -p "`$DIR"
tar xzf /tmp/humsaadhak-release.tgz -C "`$DIR"
rm -f /tmp/humsaadhak-release.tgz
find "`$DIR" -name '*.sh' -exec sed -i 's/\r`$//' {} +
sed -i 's/\r`$//' "`$DIR/humsaadhak/deploy/.env.prod"
chmod 600 "`$DIR/humsaadhak/deploy/.env.prod"
chmod +x "`$DIR"/humsaadhak/deploy/*.sh
cd "`$DIR/humsaadhak"
if docker image inspect humsaadhak/app:latest > /dev/null 2>&1; then ASSUME_YES=1 bash deploy/deploy.sh --prod update; else ASSUME_YES=1 bash deploy/deploy.sh --prod up; fi
"@
  Write-Host ''
  Write-Host "HumSaadhak production is running at https://$(Read-Setting 'DOMAIN')" -ForegroundColor Green
  Write-Host '  On the server: deploy_prod.cmd status | logs app | backup | rollback | cron'
}
function Cmd-Remote([string] $cmd) {
  Need-Tool 'ssh' 'Windows: Settings > Apps > Optional features > OpenSSH Client'
  Need-Tool 'scp' 'Windows: Settings > Apps > Optional features > OpenSSH Client'
  if ($cmd -notmatch '^[a-z-]+$' -or ($Service -and $Service -notmatch '^[A-Za-z0-9._/-]+$')) { Fail 'only simple commands are passed to the server' }
  $extra = ''; if ($Service) { $extra = " $Service" }; if ($Follow) { $extra += ' -f' }
  Remote-Run "cd '$(Remote-Dir)/humsaadhak'`nbash deploy/deploy.sh --prod $cmd$extra"
}

function Show-PgAdmin {
  $port = PgAdmin-Port
  Write-Host "  pgAdmin: http://localhost:$port   sign in as $(Read-Setting 'PGADMIN_EMAIL') with PGADMIN_PASSWORD from $Label"
  Write-Host "           the database is listed under HumSaadhak; when it asks, its password is DB_PASSWORD from $Label"
}
function Cmd-PgAdmin {
  if (-not (Has-Profile 'pgadmin')) { Fail "pgAdmin is off: add pgadmin to COMPOSE_PROFILES in $Label and run this again" }
  $port = PgAdmin-Port
  if ($remote) {
    $sa = Ssh-Args
    Write-Host "pgAdmin runs on the server, reachable only through an SSH tunnel. In a separate window, keep this running:"
    Write-Host "  ssh $($sa -join ' ') -N -L ${port}:127.0.0.1:${port} $(Target)"
    Write-Host "then open http://localhost:$port and sign in as $(Read-Setting 'PGADMIN_EMAIL') (PGADMIN_PASSWORD in $Label)."
    return
  }
  Show-PgAdmin
  if (-not $Dry) { Start-Process "http://localhost:$port" }
}

function Show-Help {
  Write-Host @"
HumSaadhak 2.0 - deployment from Windows (Docker Desktop, PowerShell, curl.exe, tar.exe, OpenSSH). Guide: DEPLOYMENT.md

  deploy_local.cmd                 the whole stack on this computer with deploy\.env: build, start, wait, smoke tests
  deploy_local.cmd status          containers and health
  deploy_local.cmd logs [service]  last log lines (app, search, db, storage, proxy); add -Follow to keep watching
  deploy_local.cmd down            stop everything (data kept)
  deploy_local.cmd pgadmin         open pgAdmin (the database console) at http://localhost:5050
  deploy_local.cmd smtp-test       sign in to the mail server (MAIL_PROVIDER=smtp) and send one test email
  deploy_local.cmd match-eval      measure how accurately the engine matches purposes to causes and projects
  deploy_local.cmd match-train     teach the engine your reviewers' decisions (after 100+ approved matches)

  deploy_prod.cmd                  production with deploy\.env.prod. With DEPLOY_SSH_HOST: pack the release, upload it to
                                   the server and run deploy/deploy.sh --prod there (up the first time, then update with
                                   automatic rollback). Without: production on this computer (asks first; -Here skips it)
  deploy_prod.cmd status|logs|backup|rollback|cron|smoke ...   on the server (deploy/deploy.sh --prod <command>)
  deploy_prod.cmd pgadmin          the SSH tunnel command to reach pgAdmin on the server (when the pgadmin profile is on)
"@
}

# ---------------------------------------------------------------- main
# Every command hands the settings file to Docker Compose (or uploads it): fix Windows line endings first
if (Test-Path -LiteralPath $EnvFile) { Normalize-LineEndings }
$remote = $Prod -and -not $Here -and (Test-Path -LiteralPath $EnvFile) -and (Read-Setting 'DEPLOY_SSH_HOST')
if ($Command -in @('help', '-h', '--help', '/?')) { Show-Help; exit 0 }
if ($Command -eq 'pgadmin') { Cmd-PgAdmin; exit 0 }
if ($Command -eq 'smtp-test') { Cmd-SmtpTest; exit 0 }   # on this computer, also for deploy_prod.cmd
if ($remote) {
  if ($Command -eq 'up') { Cmd-RemoteUp } else { Cmd-Remote $Command }
  exit 0
}
if ($Prod -and $Command -eq 'up' -and -not $Here -and -not $Confirmed) {
  $answer = Read-Host "No production server is set (DEPLOY_SSH_HOST in $Label). Run PRODUCTION on this computer? Type yes"
  if ($answer -ne 'yes') { Fail 'cancelled. Set DEPLOY_SSH_HOST in deploy\.env.prod to deploy to the server' }
}
switch ($Command) {
  'up'     { Cmd-Up; break }
  'status' { Need-Docker; Run-Compose @('ps'); if (-not $Dry) { Write-Host ('  health: ' + (Invoke-CurlExe @('-sS', '--max-time', '10', "https://$(Read-Setting 'DOMAIN')/actuator/health"))) }; break }
  'logs'   { Need-Docker; $a = @('logs', '--tail', '200'); if ($Follow) { $a += '--follow' }; if ($Service) { $a += $Service }; Run-Compose $a; break }
  'down'   { Need-Docker; Info 'Stopping HumSaadhak (data is kept)'; Run-Compose @('stop'); break }
  'smoke'  { Cmd-Smoke; break }
  'match-eval' { Need-Docker; Info 'Measuring matching accuracy with the real models (loading them takes a minute or two)'
                 Run-Compose @('exec', '-T', 'search', 'python', '-m', 'training.golden_eval'); break }
  'match-train' { Need-Docker; Info 'Fine-tuning the embedding model on approved matches (needs at least 100; can take a while)'
                  Run-Compose @('exec', '-T', 'search', 'python', '-m', 'training.fine_tune', '/models/humsaadhak-embedding'); break }
  default  { Show-Help; Fail "unknown command: $Command" }
}
