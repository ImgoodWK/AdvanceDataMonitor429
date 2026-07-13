# Runs TeXTech documentation consistency check from repo root.
# Usage: .\.cursor\skills\textech-doc-sync-pr\scripts\run-doc-check.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$Script = Join-Path $RepoRoot "tools\doc-check\doc-consistency-check.py"

if (-not (Test-Path $Script)) {
    Write-Error "doc-consistency-check.py not found: $Script"
    exit 2
}

Set-Location $RepoRoot
Write-Host "Running: py -3 tools/doc-check/doc-consistency-check.py"
& py -3 $Script
exit $LASTEXITCODE
