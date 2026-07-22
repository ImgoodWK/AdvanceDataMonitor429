# Link this Minecraft instance to the TeXTech monorepo cardbattle-server.
# Writes TeXTech/CardBattle/server-path.txt so the mod can auto-start Node on world load.
#
# Usage (from repo root, with INSTANCE = your GTNH/.minecraft folder):
#   powershell -File tools/cardbattle/link-server-path.ps1 -InstanceRoot "D:\GTNH\instances\MyPack"

param(
  [Parameter(Mandatory = $true)]
  [string]$InstanceRoot,
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$serverDir = Join-Path $RepoRoot "cardbattle-server"
if (-not (Test-Path (Join-Path $serverDir "package.json"))) {
  Write-Error "cardbattle-server not found at $serverDir"
  exit 1
}

$frontendDist = Join-Path $RepoRoot "cardbattle-frontend\dist"
if (-not (Test-Path $frontendDist)) {
  Write-Host "Building frontend dist..."
  Push-Location (Join-Path $RepoRoot "cardbattle-frontend")
  npm.cmd run build
  Pop-Location
}

$cb = Join-Path $InstanceRoot "TeXTech\CardBattle"
New-Item -ItemType Directory -Force -Path $cb | Out-Null
$pathFile = Join-Path $cb "server-path.txt"
Set-Content -Path $pathFile -Value $serverDir -Encoding UTF8
Write-Host "Wrote $pathFile -> $serverDir"
Write-Host "Ensure npm install was run in cardbattle-server."
Write-Host "In-game: enter a world, then open http://127.0.0.1:8787/  (/textech card status)"
