<#
.SYNOPSIS
  Builds a signed Android release APK with the fork's keystore and verifies its signature.

.DESCRIPTION
  Asks for the keystore password without echoing it, passes it to Gradle only through
  environment variables of this PowerShell process, and removes them when the build ends (also on
  failure). Nothing is written to disk or to the shell history. The configuration cache is turned
  off for this run so the password is not stored in it.

  After the build it runs `apksigner verify --print-certs` and compares the SHA-256 of the
  certificate with -ExpectedSha256 (or the value in docs/SIGNING.md).

.EXAMPLE
  .\scripts\signing\build-release.ps1
  .\scripts\signing\build-release.ps1 -Task :composeApp:assembleNightlyRelease
#>
[CmdletBinding()]
param(
    [string]$Task = ":composeApp:assembleStableRelease",
    [string]$KeystorePath = (Join-Path $env:USERPROFILE ".keystores\spotube\spotube.keystore"),
    [string]$Alias = "spotube",
    [string]$ExpectedSha256 = "",
    [string[]]$GradleArgs = @()
)

$ErrorActionPreference = "Stop"
$repo = Resolve-Path (Join-Path $PSScriptRoot "..\..")

if (-not (Test-Path $KeystorePath)) {
    throw "Keystore not found at '$KeystorePath'. Restore it from your backup (docs/SIGNING.md). " +
        "Do not create a new one: existing installations would reject the update."
}

# An empty answer (Enter pressed by mistake) would only make Gradle fail later; ask again.
$secure = $null
for ($attempt = 1; $attempt -le 3; $attempt++) {
    $candidate = Read-Host -AsSecureString "Keystore password"
    if ($candidate.Length -gt 0) { $secure = $candidate; break }
    $candidate.Dispose()
    Write-Host "The password cannot be empty. Type it (it is not shown) and press Enter." -ForegroundColor Yellow
}
if (-not $secure) { throw "No keystore password entered." }
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$names = "SPOTUBE_SIGNING_STORE_FILE", "SPOTUBE_SIGNING_STORE_PASSWORD",
    "SPOTUBE_SIGNING_KEY_ALIAS", "SPOTUBE_SIGNING_KEY_PASSWORD"
try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $env:SPOTUBE_SIGNING_STORE_FILE = $KeystorePath
    $env:SPOTUBE_SIGNING_STORE_PASSWORD = $plain
    $env:SPOTUBE_SIGNING_KEY_PASSWORD = $plain   # PKCS12: same as the store password
    $env:SPOTUBE_SIGNING_KEY_ALIAS = $Alias
    $plain = $null

    Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
    Push-Location $repo
    try {
        & .\gradlew.bat $Task --no-configuration-cache @GradleArgs
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($n in $names) { Remove-Item "Env:$n" -ErrorAction SilentlyContinue }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    $secure.Dispose()
}

# Signature check (public data only).
$buildTools = Get-ChildItem (Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools") |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } | Select-Object -Last 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"

if (-not $ExpectedSha256) {
    $doc = Join-Path $repo "docs\SIGNING.md"
    if (Test-Path $doc) {
        $m = Select-String -Path $doc -Pattern 'SHA-256:\s*`([0-9a-fA-F:]{64,95})`' | Select-Object -First 1
        if ($m) { $ExpectedSha256 = $m.Matches[0].Groups[1].Value }
    }
}
$expected = ($ExpectedSha256 -replace ':', '').ToLower()

$apks = Get-ChildItem (Join-Path $repo "composeApp\build\outputs\apk") -Recurse -Filter *-release.apk |
    Sort-Object LastWriteTime -Descending
if (-not $apks) { throw "No release APK found under composeApp/build/outputs/apk." }

$failed = $false
foreach ($apk in $apks | Select-Object -First 2) {
    $out = & $apksigner verify --verbose --print-certs $apk.FullName 2>&1
    # apksigner prints "Signer #1 certificate ..." or "V2 Signer: certificate ..." depending on
    # the schemes used.
    $match = $out | Select-String '(?:Signer #1|V\d+(?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-f]+)' |
        Select-Object -First 1
    $sha = if ($match) { $match.Matches[0].Groups[1].Value } else { "" }
    Write-Host ""
    Write-Host "APK:     $($apk.FullName)"
    Write-Host "Verified:" (($out | Select-String '^Verifies$') -ne $null)
    $out | Select-String 'Verified using v[0-9]+ scheme' | ForEach-Object { Write-Host "  $_" }
    Write-Host "SHA-256: $sha"
    if (-not $sha) {
        Write-Host "Could not read the certificate SHA-256 from apksigner." -ForegroundColor Red
        $failed = $true
    } elseif ($expected) {
        if ($sha -eq $expected) { Write-Host "Matches the fork's signing identity." }
        else { Write-Host "DOES NOT MATCH the expected identity ($expected)." -ForegroundColor Red; $failed = $true }
    }
}
if ($failed) { exit 1 }
