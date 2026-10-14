# Build helper: runs Gradle with the project-local JDK and Android SDK.
# Usage:  .\tools\gw.ps1 :core:test            (any Gradle arguments)
# Output is shown and also written (UTF-8) to build-log.txt; the last line is "BUILD_DONE exit=<code>".
param([Parameter(ValueFromRemainingArguments = $true)] $GradleArgs)

$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root '.toolchain\jdk17'
$env:ANDROID_HOME = Join-Path $root '.toolchain\android-sdk'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location $root
$log = Join-Path $root 'build-log.txt'
Set-Content -Path $log -Value '' -Encoding UTF8

function Run-Logged([string]$commandLine) {
    cmd.exe /c $commandLine 2>&1 | ForEach-Object {
        Write-Host $_
        Add-Content -Path $log -Value $_ -Encoding UTF8
    }
    return $LASTEXITCODE
}

if (-not (Test-Path (Join-Path $root 'gradlew.bat'))) {
    Write-Host 'Generating Gradle wrapper (pinned distribution checksum)...'
    $gradle = Join-Path $root '.toolchain\gradle\bin\gradle.bat'
    Run-Logged "`"$gradle`" wrapper --gradle-version 9.8.1 --gradle-distribution-sha256-sum dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f" | Out-Null
}

$argLine = ($GradleArgs | ForEach-Object { if ("$_" -match '\s') { '"' + $_ + '"' } else { "$_" } }) -join ' '
$code = 0
if ($argLine) {
    Run-Logged "`"$root\gradlew.bat`" $argLine" | Out-Null
    $code = $LASTEXITCODE
}
Add-Content -Path $log -Value "BUILD_DONE exit=$code" -Encoding UTF8
Write-Host "BUILD_DONE exit=$code"
