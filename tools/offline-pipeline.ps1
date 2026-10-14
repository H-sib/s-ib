# Runs every mechanical release step with no AI involved: tests, builds, manifest and hygiene checks, the signed bundle.
# Safe to run again at any time. Results go to offline-report.txt; each Gradle step keeps its full log in offline-logs\.
# Usage (from the project folder):  powershell -ExecutionPolicy Bypass -File .\tools\offline-pipeline.ps1
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$report = Join-Path $root 'offline-report.txt'
$logs = Join-Path $root 'offline-logs'
New-Item -ItemType Directory -Force $logs | Out-Null
Set-Content -Path $report -Value '' -Encoding UTF8

function Log([string]$message) {
    $line = '[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $message
    Write-Host $line
    Add-Content -Path $report -Value $line -Encoding UTF8
}

# A build started elsewhere writes BUILD_DONE to build-log.txt when it ends. Wait for it so two builds never fight.
$buildLog = Join-Path $root 'build-log.txt'
$waited = 0
# Only a log written in the last two minutes counts as a build in progress; an older one was left by an interrupted run.
while ((Test-Path $buildLog) -and ((Get-Item $buildLog).LastWriteTime -gt (Get-Date).AddMinutes(-2)) -and -not (Select-String -Path $buildLog -Pattern 'BUILD_DONE' -Quiet) -and $waited -lt 2400) {
    Start-Sleep -Seconds 15
    $waited += 15
}

function Gradle([string]$name, [string[]]$gradleArgs) {
    Log "START $name  (gradle $($gradleArgs -join ' '))"
    & (Join-Path $root 'tools\gw.ps1') @gradleArgs | Out-Null
    $tail = Get-Content $buildLog -Tail 3 | Where-Object { $_ -match 'BUILD_DONE' }
    $copy = Join-Path $logs ($name + '.txt')
    Copy-Item $buildLog $copy -Force
    if ($tail -match 'exit=0') { Log "PASS  $name"; return $true }
    Log "FAIL  $name  (see offline-logs\$name.txt)"
    Select-String -Path $copy -Pattern '^e: |FAILED|What went wrong' -Context 0,2 | Select-Object -First 12 | ForEach-Object { Log ('      ' + $_.Line.Trim()) }
    return $false
}

Log 'Offline pipeline started.'

# 1. Core engine: unit tests and the "no Android imports" rule.
Gradle '01-core-check' @('--continue', ':core:check') | Out-Null

# 2. Evaluation numbers for the date reader (optional, only informative).
Gradle '02-eval-report' @('--continue', ':eval:run', '--args=report') | Out-Null

# 3. Debug build and the app's own tests (Robolectric, Compose screenshots when present).
Gradle '03-debug-build' @('--continue', ':app:assembleDebug') | Out-Null
Gradle '04-app-tests' @('--continue', ':app:testDebugUnitTest') | Out-Null

# 4. Lint.
Gradle '05-lint' @('--continue', ':app:lintDebug') | Out-Null

# 5. Upload key: created once, kept out of version control. The password is never printed.
$props = Join-Path $root 'release\keystore.properties'
if (-not (Test-Path $props)) {
    Log 'Creating the upload key (first run only).'
    New-Item -ItemType Directory -Force (Join-Path $root 'release') | Out-Null
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $pw = [Convert]::ToBase64String($bytes).Replace('+', 'A').Replace('/', 'b').Replace('=', '')
    $keytool = Join-Path $root '.toolchain\jdk17\bin\keytool.exe'
    $store = Join-Path $root 'release\upload-keystore.p12'
    & $keytool -genkeypair -storetype PKCS12 -keystore $store -alias s-ib-upload -keyalg RSA -keysize 4096 -validity 10000 -storepass $pw -keypass $pw -dname 'CN=H-sib' 2>&1 | Out-Null
    if (Test-Path $store) {
        Set-Content -Path $props -Encoding ASCII -Value @('storeFile=release/upload-keystore.p12', "storePassword=$pw", 'keyAlias=s-ib-upload', "keyPassword=$pw")
        Log 'Upload key created in release\ (keystore.properties holds the password; back both up somewhere safe).'
    } else {
        Log 'FAIL  the upload key could not be created.'
    }
}

# 6. Signed release bundle and APK.
$bundleOk = Gradle '06-release-bundle' @('--continue', ':app:bundleRelease')
Gradle '07-release-apk' @('--continue', ':app:assembleRelease') | Out-Null

# 7. Facts about what was built.
$manifests = Get-ChildItem -Path (Join-Path $root 'app\build\intermediates\merged_manifests') -Recurse -Filter AndroidManifest.xml -ErrorAction SilentlyContinue
foreach ($m in $manifests) {
    $perms = Select-String -Path $m.FullName -Pattern 'uses-permission[^>]*android:name="([^"]+)"' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    Log ("PERMISSIONS in " + $m.FullName.Replace($root, '.') + ': ' + ($perms -join ', '))
    if ($perms -contains 'android.permission.INTERNET') { Log 'FAIL  INTERNET is present in a merged manifest. S-IB promises not to have it; remove it with tools:node="remove".' }
}

$aab = Get-ChildItem -Path (Join-Path $root 'app\build\outputs\bundle\release') -Filter *.aab -ErrorAction SilentlyContinue | Select-Object -First 1
if ($aab) {
    New-Item -ItemType Directory -Force (Join-Path $root 'release') | Out-Null
    $dest = Join-Path $root 'release\s-ib-release.aab'
    Copy-Item $aab.FullName $dest -Force
    $hash = (Get-FileHash $dest -Algorithm SHA256).Hash
    Log ("BUNDLE  release\s-ib-release.aab  {0:N1} MB  sha256 {1}" -f ((Get-Item $dest).Length / 1MB), $hash)
    $jarsigner = Join-Path $root '.toolchain\jdk17\bin\jarsigner.exe'
    $verify = & $jarsigner -verify $dest 2>&1 | Select-Object -First 1
    Log ("SIGNATURE  " + $verify)
} else {
    Log 'No release bundle was produced.'
}

$apk = Get-ChildItem -Path (Join-Path $root 'app\build\outputs\apk\release') -Filter *.apk -ErrorAction SilentlyContinue | Select-Object -First 1
$zipalign = Get-ChildItem -Path (Join-Path $root '.toolchain\android-sdk\build-tools') -Recurse -Filter zipalign.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if ($apk -and $zipalign) {
    $check = & $zipalign.FullName -c -P 16 -v 4 $apk.FullName 2>&1 | Select-Object -Last 1
    Log ("16 KB ALIGNMENT  " + $check)
}

# 8. Style rules: no em or en dashes anywhere, no unused imports.
$py = Get-Command python -ErrorAction SilentlyContinue
if ($py) {
    Log 'HYGIENE'
    & python -I (Join-Path $root 'tools\hygiene.py') 2>&1 | ForEach-Object { Log ('   ' + $_) }
}

Log 'Offline pipeline finished. Read this file, then open offline-logs\ for any step marked FAIL.'
