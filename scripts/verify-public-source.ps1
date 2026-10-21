param([string]$ProjectRoot = '')

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = Join-Path $PSScriptRoot '..' }
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$excludedSegments = @('.git', '.gradle', '.gradle-user-home', 'artifacts', 'build',
    'evidence', 'libs', 'reports', 'research', 'runs', 'tools')
$files = @(Get-ChildItem -LiteralPath $root -Recurse -Force -File | Where-Object {
    $relative = $_.FullName.Substring($root.Length + 1).Replace('\', '/')
    $segments = $relative.Split('/')
    -not @($segments | Where-Object { $_ -in $excludedSegments }).Count
})

foreach ($required in @('.gitattributes', 'LICENSE', 'NOTICE', 'README.md',
        'README_en.md', 'AI-GENERATED.md', 'VALIDATION.md',
        'THIRD-PARTY-NOTICES.md', 'LICENSE-LEGACY-README.txt', 'build.gradle',
        'dependencies.sha256.json', 'gradle.properties', 'gradlew', 'gradlew.bat',
        'gradle/wrapper/gradle-wrapper.jar',
        'src/main/java/org/scex/slashbladelegacy/LegacyCompat.java',
        'src/main/java/org/scex/slashbladelegacy/client/LegacyBladeMesh.java',
        'src/contracts/java/org/scex/slashbladelegacy/contracts/Contracts.java')) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $required) -PathType Leaf)) {
        throw "Required public source file is missing: $required"
    }
}

if (Test-Path -LiteralPath (Join-Path $root '.git')) {
    $tracked = @(& git -C $root ls-files)
    foreach ($forbidden in @('libs', 'reports', 'research', 'runs', 'tools')) {
        if (@($tracked | Where-Object { $_ -match "(^|/)$forbidden/" }).Count) {
            throw "Internal or dependency directory is tracked: $forbidden"
        }
    }
}

$unexpectedJars = @($files | Where-Object {
    $relative = $_.FullName.Substring($root.Length + 1).Replace('\', '/')
    $_.Extension -ieq '.jar' -and $relative -cnotin @('gradle/wrapper/gradle-wrapper.jar', 'ports/1.20.1/gradle/wrapper/gradle-wrapper.jar')
})
if ($unexpectedJars.Count) { throw 'Third-party or build JAR present in public source.' }

$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -File)
$javaFiles = @($sourceFiles | Where-Object Extension -eq '.java')
if ($sourceFiles.Count -ne 241 -or $javaFiles.Count -ne 217) {
    throw "Unexpected source inventory: $($sourceFiles.Count) files / $($javaFiles.Count) Java"
}

$metadata = Get-Content -Raw -LiteralPath (Join-Path $root 'src/main/resources/META-INF/neoforge.mods.toml')
if ($metadata -notmatch 'displayName="SCEX SlashBlade Legacy Compatibility"' -or
        $metadata -notmatch 'modId="slashblade_legacy_compat"') {
    throw 'Formal display name or stable mod ID is missing from metadata.'
}

$port = Join-Path $root 'ports/1.20.1'
$portSource = @(Get-ChildItem -LiteralPath (Join-Path $port 'src') -Recurse -File)
if ($portSource.Count -ne 263 -or @($portSource | Where-Object Extension -eq '.java').Count -ne 236) {
    throw 'Unexpected Minecraft 1.20.1 source inventory.'
}
foreach ($required in @('build.gradle', 'gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar',
        'dependencies.sha256.json', 'prepare-dependencies.ps1', 'README.md', 'LICENSE.md', 'SOURCE-MANIFEST.sha256')) {
    if (-not (Test-Path -LiteralPath (Join-Path $port $required) -PathType Leaf)) {
        throw "Required 1.20.1 public source file is missing: $required"
    }
}
foreach ($line in Get-Content -LiteralPath (Join-Path $port 'SOURCE-MANIFEST.sha256')) {
    if ($line -notmatch '^([a-f0-9]{64})  (.+)$') { throw 'Invalid 1.20.1 source manifest entry.' }
    $expected = $Matches[1]
    $path = Join-Path $port $Matches[2]
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expected) {
        throw "1.20.1 source hash mismatch: $($Matches[2])"
    }
}

$self = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'verify-public-source.ps1')).Path
$textExtensions = @('.gradle', '.java', '.json', '.md', '.properties', '.ps1',
    '.sh', '.toml', '.txt', '.yaml', '.yml')
$rules = @(
    [pscustomobject]@{ Name = 'local machine path'; Pattern = '(?i)(?:C|D|E):[\\/]' },
    [pscustomobject]@{ Name = 'GitHub credential'; Pattern = '(?:github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,})' },
    [pscustomobject]@{ Name = 'cloud/API credential'; Pattern = '(?:AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})' },
    [pscustomobject]@{ Name = 'private key'; Pattern = '-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----' }
)
$findings = [System.Collections.Generic.List[string]]::new()
foreach ($file in $files) {
    if ($file.FullName -ceq $self -or $file.Extension.ToLowerInvariant() -notin $textExtensions) { continue }
    $relative = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
    $content = [System.IO.File]::ReadAllText($file.FullName)
    foreach ($rule in $rules) {
        if ($content -match $rule.Pattern) { $findings.Add("$($rule.Name): $relative") }
    }
}
if ($findings.Count) { throw "Public-source audit failed:`n$($findings -join [Environment]::NewLine)" }

$large = @($files | Where-Object { $_.Length -gt 50MB })
if ($large.Count) { throw 'A public source file exceeds 50 MiB.' }

[pscustomobject]@{
    ProjectRoot = $root
    PublicFiles = $files.Count
    SourceFiles = $sourceFiles.Count
    JavaFiles = $javaFiles.Count
    Findings = 0
    UnexpectedDependencyJars = 0
    LargeFiles = 0
    Verification = 'PASS: identity, source inventory, dependency-binary, local-path, credential, provenance, and size gates'
}
