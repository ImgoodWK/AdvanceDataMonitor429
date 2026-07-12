# Sync GTNH BetterQuesting fixtures from upstream modpack into dev-fixtures/betterquesting/
# Usage: powershell -ExecutionPolicy Bypass -File tools/dev/sync-betterquesting-from-gtnh.ps1

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$upstream = 'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack.git'
$sparsePath = 'config/betterquesting'
$dest = Join-Path $repoRoot 'dev-fixtures\betterquesting'
$scratch = Join-Path $repoRoot '.workspace\gtnh-bq-sync-scratch'

Write-Host "TeXTech: refreshing BetterQuesting fixtures -> $dest"

if (Test-Path $scratch) {
    Remove-Item -Recurse -Force $scratch
}

git clone --depth 1 --filter=blob:none --sparse $upstream $scratch | Out-Null
Push-Location $scratch
git sparse-checkout set $sparsePath | Out-Null
$commit = (git rev-parse HEAD).Trim()
Pop-Location

$src = Join-Path $scratch $sparsePath
if (-not (Test-Path $src)) {
    throw "Upstream path missing: $src"
}

# Preserve TeXTech metadata files across refresh
$metaFiles = @('SOURCE.json', 'README-dev.md')
$metaBackup = Join-Path $repoRoot '.workspace\bq-meta-backup'
if (Test-Path $metaBackup) { Remove-Item -Recurse -Force $metaBackup }
New-Item -ItemType Directory -Path $metaBackup -Force | Out-Null
foreach ($name in $metaFiles) {
    $p = Join-Path $dest $name
    if (Test-Path $p) {
        Copy-Item $p (Join-Path $metaBackup $name) -Force
    }
}

if (Test-Path $dest) {
    Get-ChildItem $dest -Force | Where-Object { $_.Name -ne 'SOURCE.json' -and $_.Name -ne 'README-dev.md' } | Remove-Item -Recurse -Force
} else {
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
}

Copy-Item -Path (Join-Path $src '*') -Destination $dest -Recurse -Force

foreach ($name in $metaFiles) {
    $backup = Join-Path $metaBackup $name
    if (Test-Path $backup) {
        Copy-Item $backup (Join-Path $dest $name) -Force
    }
}

$sourceJson = @{
    upstream = 'GTNewHorizons/GT-New-Horizons-Modpack'
    upstreamPath = $sparsePath
    commit = $commit
    betterQuestingVersion = '3.8.70-GTNH'
    syncedAt = (Get-Date -Format 'yyyy-MM-dd')
    notes = 'Dev-only quest data for runClient/runServer. Refresh with tools/dev/sync-betterquesting-from-gtnh.ps1'
} | ConvertTo-Json -Depth 4

Set-Content -Path (Join-Path $dest 'SOURCE.json') -Value $sourceJson -Encoding UTF8

Remove-Item -Recurse -Force $scratch
Remove-Item -Recurse -Force $metaBackup -ErrorAction SilentlyContinue

$fileCount = (Get-ChildItem $dest -Recurse -File).Count
Write-Host "Done. commit=$commit files=$fileCount"
Write-Host "Next: git add dev-fixtures/betterquesting && gradlew syncDevBetterQuesting"
