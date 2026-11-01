@echo off
rem HumSaadhak 2.0 - the whole stack on THIS computer (Docker Desktop) with deploy\.env and the local database.
rem Double-click it, or run it from a Command Prompt in this folder. Needs Docker Desktop.
rem   deploy_local.cmd                  build, start, wait until healthy, smoke tests; then open https://localhost
rem   deploy_local.cmd status           containers and health
rem   deploy_local.cmd logs [service]   last log lines (app, search, db, storage, proxy); add -Follow to keep watching
rem   deploy_local.cmd down             stop everything (data kept)
rem The work is done by deploy\deploy.ps1 (started with -ExecutionPolicy Bypass, so Windows script blocking does not apply).
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\deploy.ps1" %*
set "RC=%ERRORLEVEL%"
rem Started by double-click: keep the window open so the result can be read
echo %CMDCMDLINE% | findstr /i /c:"%~nx0" >nul && pause
exit /b %RC%
