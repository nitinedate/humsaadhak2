@echo off
rem HumSaadhak 2.0 - PRODUCTION with deploy\.env.prod (managed database, real email).
rem Double-click it, or run it from a Command Prompt in this folder. Needs Docker Desktop (and for a production server: the OpenSSH client).
rem   deploy_prod.cmd                   DEPLOY_SSH_HOST set in deploy\.env.prod: pack the release, upload it to that Linux
rem                                     server and run deploy/deploy.sh --prod there (first time up, then update with
rem                                     automatic rollback). Not set: production on this computer (asks first).
rem   deploy_prod.cmd status            on the server; also: logs app, backup, rollback, cron, smoke, dump-legacy
rem The work is done by deploy\deploy.ps1 (started with -ExecutionPolicy Bypass, so Windows script blocking does not apply).
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\deploy.ps1" -Prod %*
set "RC=%ERRORLEVEL%"
rem Started by double-click: keep the window open so the result can be read
echo %CMDCMDLINE% | findstr /i /c:"%~nx0" >nul && pause
exit /b %RC%
