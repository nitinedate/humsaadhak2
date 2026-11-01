<#
.SYNOPSIS
  HumSaadhak 2.0 - the PostgreSQL 16 + pgvector database alone in Docker, for development on Windows (Docker Desktop).
  The same as deploy/db.sh (Linux, macOS, WSL, Git Bash); the whole stack, database included, is deploy/deploy.sh up.

.EXAMPLE
  .\deploy\db.ps1 start                       # create (first time), start, wait until ready, show how to connect
  .\deploy\db.ps1 env | Invoke-Expression     # set DB_URL, DB_USER, DB_PASSWORD ... in this PowerShell window
  mvn -f backend spring-boot:run              # then run the backend (Amazon Corretto 17 and Maven)
  .\deploy\db.ps1 help                        # every command

.NOTES
  Settings: DB_NAME, DB_USER, DB_PASSWORD from deploy\.env (with the filled file: saadhak_demo, postgres, admin).
  Without deploy\.env: humsaadhak / humsaadhak and a random password kept in deploy\.env.db.
  -Port (or DB_PORT_LOCAL): port on this computer, bound to 127.0.0.1 only. -Tls (or DB_TLS=1): TLS with the internal
  certificates in deploy\tls\out (make them first with deploy/tls/make-certs.sh in Git Bash or WSL).
  If scripts are blocked: powershell -ExecutionPolicy Bypass -File .\deploy\db.ps1 start
  Works in Windows PowerShell 5.1 and PowerShell 7.
#>
param(
  [Parameter(Position = 0)] [string] $Command = 'start',
  [Parameter(Position = 1)] [string] $Path = '',
  [int] $Port = 0,
  [switch] $Tls,
  [switch] $Follow,
  [switch] $Yes,
  [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$DeployDir = $PSScriptRoot
$RepoDir = Split-Path -Parent $DeployDir
$EnvFile = Join-Path $DeployDir '.env'
$DevEnvFile = Join-Path $DeployDir '.env.db'
$Container = 'humsaadhak-db-dev'
$Volume = 'humsaadhak-db-dev-data'
$Image = 'pgvector/pgvector:pg16'
if ($Port -eq 0) { if ($env:DB_PORT_LOCAL) { $Port = [int]$env:DB_PORT_LOCAL } else { $Port = 5432 } }
$UseTls = $Tls.IsPresent -or ($env:DB_TLS -eq '1')
$Dry = $DryRun.IsPresent -or ($env:DRY_RUN -eq '1')
$Confirmed = $Yes.IsPresent -or ($env:ASSUME_YES -eq '1')

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  ok   $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  warn $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "failed: $m" -ForegroundColor Red; exit 1 }

function Read-Setting([string] $file, [string] $key) {
  if (-not (Test-Path $file)) { return '' }
  $value = ''
  foreach ($line in Get-Content -LiteralPath $file) {
    if ($line -match "^$key=(.*)$") { $value = $Matches[1] }
  }
  return $value
}
function Pick([string] $envValue, [string] $fileValue) { if ($envValue) { return $envValue } return $fileValue }

# Windows PowerShell 5.1 turns a native program's redirected stderr into errors, and with ErrorActionPreference=Stop the
# first such line (curl's "could not connect" while the proxy starts, docker's "No such container") ends the script.
# Native programs report failure through their exit code, which the callers check: run them with errors not stopping.
function Invoke-Quiet([scriptblock] $block) {
  $saved = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { & $block } finally { $ErrorActionPreference = $saved }
}

# ---------------------------------------------------------------- settings: environment > deploy\.env > deploy\.env.db
$DbName = Pick $env:DB_NAME (Read-Setting $EnvFile 'DB_NAME')
$DbUser = Pick $env:DB_USER (Read-Setting $EnvFile 'DB_USER')
$DbPassword = Pick $env:DB_PASSWORD (Read-Setting $EnvFile 'DB_PASSWORD')
$PasswordFrom = 'deploy\.env'
if (-not $DbPassword) {
  if (-not $DbName) { $DbName = Read-Setting $DevEnvFile 'DB_NAME' }
  if (-not $DbUser) { $DbUser = Read-Setting $DevEnvFile 'DB_USER' }
  $DbPassword = Read-Setting $DevEnvFile 'DB_PASSWORD'
  $PasswordFrom = 'deploy\.env.db'
}
if (-not $DbName) { $DbName = 'humsaadhak' }
if (-not $DbUser) { $DbUser = 'humsaadhak' }
if ($DbName -notmatch '^[a-z_][a-z0-9_]*$') { Fail 'DB_NAME may contain only lowercase letters, digits and _' }
if ($DbUser -notmatch '^[a-z_][a-z0-9_]*$') { Fail 'DB_USER may contain only lowercase letters, digits and _' }

function Ensure-Password {
  if ($script:DbPassword) { return }
  $bytes = New-Object byte[] 16
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $script:DbPassword = -join ($bytes | ForEach-Object { $_.ToString('x2') })
  $text = "# Local development database (deploy\db.ps1). Never commit.`nDB_NAME=$DbName`nDB_USER=$DbUser`nDB_PASSWORD=$($script:DbPassword)`n"
  [System.IO.File]::WriteAllText($DevEnvFile, $text)
  Ok 'no deploy\.env: made a password and kept it in deploy\.env.db'
}

# ---------------------------------------------------------------- docker
# Not named Docker: PowerShell would then call this function for every `docker`, itself included.
function Run-Docker([string[]] $arguments, [switch] $Quiet) {
  if ($Dry) { Write-Host ("  [dry-run] docker " + ($arguments -join ' ')); return '' }
  $output = & docker @arguments
  if ($LASTEXITCODE -ne 0) { Fail ("docker " + $arguments[0] + " failed (exit $LASTEXITCODE)") }
  if (-not $Quiet) { return $output }
}
function Need-Docker {
  if ($Dry) { return }
  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail 'Docker is not installed: install Docker Desktop' }
  Invoke-Quiet { & docker info *> $null }
  if ($LASTEXITCODE -ne 0) { Fail 'Docker Desktop is not running: start it and try again' }
}
function Exists { if ($Dry) { return $false }; Invoke-Quiet { & docker container inspect $Container *> $null }; return ($LASTEXITCODE -eq 0) }
function Running {
  if ($Dry) { return $true }
  $state = Invoke-Quiet { & docker container inspect -f '{{.State.Running}}' $Container 2> $null }
  return ($LASTEXITCODE -eq 0 -and "$state".Trim() -eq 'true')
}
# Labels are read as JSON: Windows PowerShell 5.1 mangles double quotes inside arguments to native programs, so no
# argument in this script contains one.
function Label([string] $name) {
  $labels = (& docker container inspect -f '{{json .Config.Labels}}' $Container) | ConvertFrom-Json
  $value = $labels.PSObject.Properties[$name]
  if ($value) { return "$($value.Value)".Trim() } return ''
}
function ConfigId { return "port=$Port tls=$([int]$UseTls) image=$Image" }
function InDb([string[]] $arguments) { return Run-Docker (@('exec', '-i', $Container) + $arguments) }
function Psql([string] $db, [string] $sql) { return InDb @('psql', '-U', $DbUser, '-d', $db, '-Atqc', $sql) }

function Jdbc-Url {
  $url = "jdbc:postgresql://localhost:$Port/$DbName"
  if ($UseTls) { $url += "?sslmode=verify-full&sslrootcert=" + ((Join-Path $DeployDir 'tls\out\ca.crt') -replace '\\', '/') }
  return $url
}

# ---------------------------------------------------------------- start
function Create-Container {
  $arguments = @('run', '-d', '--name', $Container, '--restart', 'unless-stopped',
    '--label', "humsaadhak.db.config=$(ConfigId)", '--label', "humsaadhak.db.user=$DbUser",
    '-e', "POSTGRES_DB=$DbName", '-e', "POSTGRES_USER=$DbUser", '-e', "POSTGRES_PASSWORD=$DbPassword",
    '-p', "127.0.0.1:${Port}:5432", '-v', "${Volume}:/var/lib/postgresql/data",
    '--health-cmd', "pg_isready -U $DbUser -d postgres", '--health-interval', '3s', '--health-timeout', '3s', '--health-retries', '40')
  if ($UseTls) {
    $certs = Join-Path $DeployDir 'tls\out\db'
    if (-not (Test-Path (Join-Path $certs 'tls.key'))) { Fail 'no internal certificates: run deploy/tls/make-certs.sh in Git Bash or WSL first' }
    $hba = Join-Path $DeployDir 'postgres\pg_hba.conf'
    $start = 'mkdir -p /tls && install -o postgres -g postgres -m 600 /tls-src/tls.key /tls/tls.key && install -o postgres -g postgres -m 644 /tls-src/tls.crt /tls/tls.crt && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tls/tls.crt -c ssl_key_file=/tls/tls.key -c ssl_min_protocol_version=TLSv1.2 -c hba_file=/etc/postgresql/pg_hba.conf'
    $arguments += @('-v', "${certs}:/tls-src:ro", '-v', "${hba}:/etc/postgresql/pg_hba.conf:ro", '--entrypoint', 'bash', $Image, '-c', $start)
  } else {
    $arguments += @($Image)
  }
  Run-Docker $arguments -Quiet
}

function Wait-Ready {
  if ($Dry) { return }
  for ($i = 0; $i -lt 60; $i++) {
    $status = ("" + (Invoke-Quiet { & docker container inspect -f '{{.State.Health.Status}}' $Container 2> $null })).Trim()
    if ($status -eq 'healthy') { return }
    if ($LASTEXITCODE -ne 0) { Fail 'the container stopped: .\deploy\db.ps1 logs' }
    Start-Sleep -Seconds 2
  }
  & docker logs --tail 30 $Container
  Fail 'the database did not become ready'
}

function Cmd-Start {
  Need-Docker; Ensure-Password
  if (Exists) {
    $user = Label 'humsaadhak.db.user'
    if ($user -and $user -ne $DbUser) { Fail "the data was created for user $user, but DB_USER is now ${DbUser}: keep $user, or delete the data with .\deploy\db.ps1 reset" }
    $config = Label 'humsaadhak.db.config'
    if ($config -ne (ConfigId)) {
      Info "Settings changed ($config -> $(ConfigId)): recreating the container, keeping the data"
      Run-Docker @('rm', '-f', $Container) -Quiet; Create-Container
    } elseif (-not (Running)) { Info "Starting $Container"; Run-Docker @('start', $Container) -Quiet }
    else { Ok "$Container is already running" }
  } else {
    Info "Creating $Container ($Image, data in volume $Volume)"
    Create-Container
  }
  Wait-Ready
  if (-not $Dry) {
    Invoke-Quiet { & docker exec $Container psql -U $DbUser -d postgres -Atc 'select 1' *> $null }
    if ($LASTEXITCODE -ne 0) { Fail "user $DbUser cannot connect: the data volume was made with other settings (.\deploy\db.ps1 reset starts afresh)" }
    $found = ("" + (Psql 'postgres' "select 1 from pg_database where datname = '$DbName'")).Trim()
    if ($found -ne '1') { Psql 'postgres' "create database $DbName" | Out-Null; Ok "created database $DbName" }
    Psql $DbName 'create extension if not exists vector' | Out-Null
  }
  $mode = ''; if ($UseTls) { $mode = ' (TLS only)' }
  Ok "PostgreSQL 16 with pgvector is ready on localhost:$Port$mode"
  Write-Host ""
  Write-Host "  Database   $DbName   user $DbUser   (password in $PasswordFrom)"
  Write-Host "  JDBC URL   $(Jdbc-Url)"
  Write-Host "  psql       .\deploy\db.ps1 psql"
  Write-Host "  Backend    .\deploy\db.ps1 env | Invoke-Expression; mvn -f backend spring-boot:run   (Amazon Corretto 17 and Maven)"
}

# ---------------------------------------------------------------- env: PowerShell lines that set the backend's settings
function Quote([string] $v) { return "'" + ($v -replace "'", "''") + "'" }
function Cmd-Env {
  Ensure-Password
  $lines = @("`$env:DB_URL = $(Quote (Jdbc-Url))", "`$env:DB_USER = $(Quote $DbUser)", "`$env:DB_PASSWORD = $(Quote $DbPassword)")
  $pg = "postgresql://${DbUser}:${DbPassword}@localhost:$Port/$DbName"
  if ($UseTls) { $pg += "?sslmode=verify-full&sslrootcert=" + ((Join-Path $DeployDir 'tls\out\ca.crt') -replace '\\', '/') }
  $lines += "`$env:DATABASE_URL = $(Quote $pg)"
  foreach ($k in @('HUMSAADHAK_FIELD_KEY', 'SEARCH_TOKEN', 'HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL', 'MAIL_PROVIDER', 'MAIL_FROM',
                   'MAIL_REDIRECT_TO', 'MAIL_LOG_LINKS', 'GRAPH_TENANT_ID', 'GRAPH_CLIENT_ID', 'GRAPH_CLIENT_SECRET')) {
    $v = Read-Setting $EnvFile $k
    if ($v) { $lines += "`$env:$k = $(Quote $v)" }
  }
  $lines += "`$env:APP_URL = 'http://localhost:8080'"
  $lines | ForEach-Object { Write-Output $_ }
}

# ---------------------------------------------------------------- the rest (dump files go through docker cp: binary-safe)
function Need-Running { Need-Docker; if (-not (Running)) { Fail 'not running: .\deploy\db.ps1 start' } }

function Cmd-Stop {
  Need-Docker
  if (-not (Exists) -and -not $Dry) { Ok 'not created yet'; return }
  Info "Stopping $Container (data kept)"; Run-Docker @('stop', $Container) -Quiet; Ok 'stopped'
}

function Cmd-Status {
  Need-Docker
  if (-not (Exists)) { Write-Host "  ${Container}: not created (.\deploy\db.ps1 start)"; return }
  & docker ps -a --filter "name=^${Container}$" --format '  {{.Names}}: {{.Status}}, {{.Ports}}'
  if (Running) {
    $size = ("" + (Psql $DbName 'select pg_size_pretty(pg_database_size(current_database()))')).Trim()
    $tables = ("" + (Psql $DbName "select count(*) from information_schema.tables where table_schema not in ('pg_catalog','information_schema')")).Trim()
    Write-Host "  database ${DbName}: $size, $tables tables"
  }
}

function Cmd-Logs { Need-Docker; if ($Follow) { Run-Docker @('logs', '--tail', '100', '-f', $Container) } else { Run-Docker @('logs', '--tail', '100', $Container) } }

function Cmd-Psql {
  Need-Running
  if ($Path) { Run-Docker @('exec', '-i', $Container, 'psql', '-U', $DbUser, '-d', $DbName, '-c', $Path) }
  elseif ($Dry) { Run-Docker @('exec', '-it', $Container, 'psql', '-U', $DbUser, '-d', $DbName) }
  else { & docker exec -it $Container psql -U $DbUser -d $DbName }
}

function Cmd-Dump {
  Need-Running
  $out = $Path
  if (-not $out) { $out = Join-Path $RepoDir ("backups\dev-$DbName-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump') }
  $dir = Split-Path -Parent $out
  if (-not $Dry -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
  Info "Dumping $DbName to $out"
  InDb @('pg_dump', '-U', $DbUser, '-d', $DbName, '-Fc', '-f', '/tmp/humsaadhak.dump') | Out-Null
  Run-Docker @('cp', "${Container}:/tmp/humsaadhak.dump", $out) -Quiet
  InDb @('rm', '-f', '/tmp/humsaadhak.dump') | Out-Null
  if (-not $Dry) { Ok ("{0:N1} MB written" -f ((Get-Item $out).Length / 1MB)) }
}

# Loads a dump inside the container: custom format with pg_restore, plain SQL (.sql, .sql.gz) with psql
function Load-Into([string] $db, [string] $file) {
  $name = '/tmp/humsaadhak-load.dump'; if ($file -like '*.gz') { $name += '.gz' }
  Run-Docker @('cp', $file, "${Container}:$name") -Quiet
  # No double quotes in this script (see Label); the file name, user and database have no spaces.
  $script = 'f=$1; u=$2; d=$3; case $f in *.gz) r=''gzip -dc'';; *) r=cat;; esac; m=$($r $f | head -c 5); ' +
            'if [ x$m = xPGDMP ]; then $r $f | pg_restore -U $u -d $d --no-owner --no-privileges; ' +
            'else $r $f | psql -U $u -d $d -v ON_ERROR_STOP=1 -q > /dev/null; fi; s=$?; rm -f $f; exit $s'
  InDb @('sh', '-c', $script, 'load', $name, $DbUser, $db) | Out-Null
}

function Confirm-Typed([string] $question, [string] $word) {
  if ($Confirmed) { return }
  $answer = Read-Host "$question Type $word to continue"
  if ($answer -ne $word) { Fail 'cancelled' }
}

function Cmd-Restore {
  if (-not $Path) { Fail 'usage: .\deploy\db.ps1 restore <dump file>' }
  if (-not $Dry -and -not (Test-Path $Path)) { Fail "$Path does not exist" }
  Need-Running
  Confirm-Typed "Replace everything in database $DbName with ${Path}?" 'RESTORE'
  Info "Restoring $Path into $DbName"
  Psql 'postgres' "drop database if exists $DbName with (force)" | Out-Null
  Psql 'postgres' "create database $DbName" | Out-Null
  Load-Into $DbName $Path
  Ok 'restored'
}

function Cmd-LoadLegacy {
  if (-not $Path) { Fail 'usage: .\deploy\db.ps1 load-legacy <old database dump>' }
  if (-not $Dry -and -not (Test-Path $Path)) { Fail "$Path does not exist" }
  Need-Running
  Info "Loading the old system's dump into schema legacy of $DbName"
  Psql 'postgres' 'drop database if exists legacy_import with (force)' | Out-Null
  Psql 'postgres' 'create database legacy_import' | Out-Null
  Load-Into 'legacy_import' $Path
  Psql 'legacy_import' 'drop schema if exists legacy cascade; alter schema public rename to legacy' | Out-Null
  Psql $DbName 'drop schema if exists legacy cascade' | Out-Null
  InDb @('sh', '-c', 'pg_dump -U $1 -d legacy_import -n legacy --no-owner --no-privileges | psql -U $1 -d $2 -v ON_ERROR_STOP=1 -q', 'move', $DbUser, $DbName) | Out-Null
  Psql 'postgres' 'drop database legacy_import' | Out-Null
  Ok 'the old data is in schema legacy'
  Write-Host '  Next, run the backend once with the migration profile (Flyway V100 copies the records, then the old files):'
  Write-Host "    .\deploy\db.ps1 env | Invoke-Expression; `$env:SPRING_PROFILES_ACTIVE = 'legacy-migration'; `$env:LEGACY_UPLOAD_DIR = 'D:\old\uploads'; mvn -f backend spring-boot:run"
  Write-Host "  Then check: .\deploy\db.ps1 psql 'select * from legacy_migration_report'"
}

function Cmd-Reset {
  Need-Docker
  Confirm-Typed "Delete $Container and ALL its data (volume $Volume)?" 'reset'
  if ($Dry) { Run-Docker @('rm', '-f', $Container) -Quiet; Run-Docker @('volume', 'rm', $Volume) -Quiet }
  else { Invoke-Quiet { & docker rm -f $Container *> $null; & docker volume rm $Volume *> $null } }
  Ok 'deleted; .\deploy\db.ps1 start creates an empty database'
}

function Show-Help {
  $mode = ''; if ($UseTls) { $mode = ', TLS only' }
  Write-Host @"
HumSaadhak 2.0 - the PostgreSQL 16 + pgvector database alone in Docker, for development (container $Container).
The whole stack, database included: deploy/deploy.sh up (Linux, WSL). On Linux, macOS, WSL and Git Bash: deploy/db.sh.

  .\deploy\db.ps1 start                 create (first time) and start; waits until ready; prints how to connect
  .\deploy\db.ps1 stop                  stop (data kept)
  .\deploy\db.ps1 status                running?, port, size, number of tables
  .\deploy\db.ps1 env | Invoke-Expression   set the backend's settings in this window, then: mvn -f backend spring-boot:run
  .\deploy\db.ps1 psql ['<sql>']        open psql, or run one statement
  .\deploy\db.ps1 logs [-Follow]        the database's log
  .\deploy\db.ps1 dump [file]           save the database to backups\dev-<name>-<date>.dump or [file]
  .\deploy\db.ps1 restore <file>        replace the database with a dump (asks you to type RESTORE)
  .\deploy\db.ps1 load-legacy <dump>    put the old system's dump into schema legacy, for one run of the legacy migration
  .\deploy\db.ps1 reset                 delete the container and all its data (asks you to type reset)

Database $DbName, user $DbUser, port $Port on 127.0.0.1$mode.  Options: -Port 5433, -Tls, -Yes, -DryRun.
"@
}

switch ($Command) {
  { $_ -in 'start', 'up' } { Cmd-Start; break }
  { $_ -in 'stop', 'down' } { Cmd-Stop; break }
  'status' { Cmd-Status; break }
  'env' { Cmd-Env; break }
  'psql' { Cmd-Psql; break }
  'logs' { Cmd-Logs; break }
  'dump' { Cmd-Dump; break }
  'restore' { Cmd-Restore; break }
  'load-legacy' { Cmd-LoadLegacy; break }
  'reset' { Cmd-Reset; break }
  { $_ -in 'help', '-h', '--help', '/?' } { Show-Help; break }
  default { Show-Help; Fail "unknown command: $Command" }
}
