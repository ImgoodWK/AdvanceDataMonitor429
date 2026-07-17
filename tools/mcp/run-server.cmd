@echo off
setlocal EnableExtensions
REM Portable launcher for TeXTech stdio MCP servers (Windows).
REM Usage: run-server.cmd tools\mcp\<server>\server.py

set "SCRIPT=%~1"
if "%SCRIPT%"=="" (
  echo Usage: run-server.cmd path\to\server.py 1>&2
  exit /b 2
)

REM Repo root = tools/mcp/../..
cd /d "%~dp0..\.."
if not exist "%SCRIPT%" (
  echo MCP script not found: %SCRIPT% 1>&2
  exit /b 2
)

REM Prefer installed CPython over Windows Store python stub
for %%P in (
  "%LocalAppData%\Programs\Python\Python314\python.exe"
  "%LocalAppData%\Programs\Python\Python313\python.exe"
  "%LocalAppData%\Programs\Python\Python312\python.exe"
  "C:\Python314\python.exe"
  "C:\Python313\python.exe"
  "C:\Python312\python.exe"
) do (
  if exist %%~P (
    %%~P -u "%SCRIPT%"
    exit /b %ERRORLEVEL%
  )
)

where py >nul 2>&1
if %ERRORLEVEL%==0 (
  py -3 -u "%SCRIPT%"
  exit /b %ERRORLEVEL%
)

where python >nul 2>&1
if %ERRORLEVEL%==0 (
  python -u "%SCRIPT%"
  exit /b %ERRORLEVEL%
)

echo No usable Python found for TeXTech MCP. Install Python 3 and ensure py/python is on PATH. 1>&2
exit /b 1
