# Publish Donut Essentials locally (Modrinth + CurseForge).
$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$LocalEnv = 'C:\Users\mahou\NightBeam-Knowledge-Base\secrets\local.env'

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -le 0) { return }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if (-not (Test-Path "env:$name")) {
            Set-Item -Path "env:$name" -Value $value
        }
    }
}

Import-DotEnv (Join-Path $RepoRoot '.env')
Import-DotEnv $LocalEnv

Push-Location $RepoRoot
try {
    node (Join-Path $PSScriptRoot 'publish-local.mjs') @args
} finally {
    Pop-Location
}
