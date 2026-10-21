#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$CacheDirectory = (Join-Path $HOME '.cache/old-dream-reforged/dependencies'),
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$libraryDirectory = Join-Path $PSScriptRoot 'libs'
$dependencies = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'dependencies.sha256.json') -Raw | ConvertFrom-Json
function Test-Dependency($Path, $Entry) {
    return (Test-Path -LiteralPath $Path -PathType Leaf) -and
        (Get-Item -LiteralPath $Path).Length -eq $Entry.size -and
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ieq $Entry.sha256
}

New-Item -ItemType Directory -Force -Path $libraryDirectory | Out-Null
foreach ($entry in $dependencies) {
    $destination = Join-Path $libraryDirectory $entry.file
    if (Test-Dependency $destination $entry) {
        Write-Host "Verified existing dependency: $($entry.file)"
        continue
    }
    if (Test-Path -LiteralPath $destination) {
        throw "Unexpected dependency content: $destination. Move it aside before preparing the pinned version."
    }
    $cached = Join-Path $CacheDirectory $entry.file
    if (-not (Test-Dependency $cached $entry)) {
        if ($Offline) { throw "Verified dependency is absent from libs and the selected cache: $($entry.file)" }
        New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null
        $temporary = Join-Path $CacheDirectory ("$($entry.file).$([Guid]::NewGuid().ToString('N')).part")
        try {
            Invoke-WebRequest -Uri $entry.download_url -OutFile $temporary -NoProxy -TimeoutSec 120
            if (-not (Test-Dependency $temporary $entry)) { throw "Downloaded dependency failed size/SHA-256 verification: $($entry.file)" }
            Move-Item -LiteralPath $temporary -Destination $cached -Force
        } finally {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
        }
    }
    Copy-Item -LiteralPath $cached -Destination $destination
    Write-Host "Prepared verified dependency: $($entry.file)"
}
