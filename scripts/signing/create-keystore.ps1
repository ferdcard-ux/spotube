<#
.SYNOPSIS
  Creates the fork's Android release keystore outside the repository. Run it ONCE, in your own
  interactive terminal.

.DESCRIPTION
  - Refuses to run if the keystore already exists: a second key would be a different signing
    identity and Android would reject it as an update.
  - keytool asks for the passwords itself, without echoing them. They never appear on the command
    line, in the shell history or in any file.
  - Restricts the folder to the current Windows user (no inherited permissions).
  - Prints the certificate's SHA-256 fingerprint (public) so it can be recorded in docs/SIGNING.md.

  Use a strong, unique password of at least 12 characters and store it in a password manager.
  The keystore is PKCS12, so the key password is the same as the store password.
#>
[CmdletBinding()]
param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE ".keystores\spotube\spotube.keystore"),
    [string]$Alias = "spotube"
)

$ErrorActionPreference = "Stop"

function Find-Keytool {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe") }
    $candidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    throw "keytool not found. Set JAVA_HOME to a JDK (17+) and try again."
}

if (Test-Path $KeystorePath) {
    throw "A keystore already exists at '$KeystorePath'. Not creating another one: a new key " +
        "would change the app's signing identity. To change its password see docs/SIGNING.md."
}

$keytool = Find-Keytool
$dir = Split-Path $KeystorePath -Parent
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# Only the current user may read the folder (and the files created in it inherit that).
$me = "$env:USERDOMAIN\$env:USERNAME"
& icacls $dir /inheritance:r /grant:r "${me}:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not restrict permissions on '$dir'." }

Write-Host ""
Write-Host "Creating the release keystore at $KeystorePath (alias '$Alias')."
Write-Host "keytool will ask for a password (hidden), then your name/organization for the certificate."
Write-Host "Use at least 12 characters and save it in your password manager."
Write-Host ""

& $keytool -genkeypair -v `
    -keystore $KeystorePath `
    -storetype PKCS12 `
    -alias $Alias `
    -keyalg RSA -keysize 4096 `
    -validity 10950
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $KeystorePath)) {
    if (Test-Path $KeystorePath) { Remove-Item $KeystorePath -Force }
    throw "keytool did not create the keystore."
}

& icacls $KeystorePath /inheritance:r /grant:r "${me}:F" | Out-Null

Write-Host ""
Write-Host "Keystore created. keytool will now ask for the password once more to show the public fingerprint:"
& $keytool -list -v -keystore $KeystorePath -alias $Alias |
    Select-String -Pattern "Alias name|Owner:|SHA256:|Valid from"

Write-Host ""
Write-Host "Next: back it up (docs/SIGNING.md, 'Backup'). Without this file and its password the app"
Write-Host "can never be updated again."
