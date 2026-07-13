param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [string]$Filter = ""
)
$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$JarPath = if ([IO.Path]::IsPathRooted($Jar)) { $Jar } else { Join-Path $RepoRoot $Jar }
if (-not (Test-Path $JarPath)) { Write-Error "Jar not found: $JarPath"; exit 2 }
Set-Location $RepoRoot
$out = & jar tf $JarPath 2>&1
if ($LASTEXITCODE -ne 0) { $out; exit $LASTEXITCODE }
if ($Filter) {
    $out | Select-String -Pattern $Filter
} else {
    $out
}
