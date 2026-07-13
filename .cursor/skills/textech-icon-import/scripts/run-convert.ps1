param(
    [Parameter(Mandatory = $true)][string]$InputDir,
    [Parameter(Mandatory = $true)][string]$OutputDir
)
$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$Script = Join-Path $RepoRoot "tools\icon-import\convert_icon_exporter.py"
Set-Location $RepoRoot
& py -3 $Script --input $InputDir --output $OutputDir
exit $LASTEXITCODE
