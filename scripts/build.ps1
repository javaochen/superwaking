# 构建 APK（便携工具链，不动系统环境变量）

$ErrorActionPreference = 'Stop'
$root = 'D:\Tools\android'
$proj = 'D:\works\app\superwaking'

$jdk = Get-ChildItem $root -Directory -Filter 'jdk-17*' | Select-Object -First 1
if (-not $jdk) { throw "找不到 JDK 17，检查 $root" }
$gradle = Get-ChildItem $root -Directory -Filter 'gradle-8*' | Select-Object -First 1
if (-not $gradle) { throw "找不到 Gradle 8，检查 $root" }

$env:JAVA_HOME = $jdk.FullName
$env:GRADLE_USER_HOME = Join-Path $root 'gradle-home'
$env:ANDROID_HOME = Join-Path $root 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Write-Host "JDK    : $($jdk.FullName)"
Write-Host "Gradle : $($gradle.FullName)"
Write-Host "SDK    : $env:ANDROID_HOME"

Push-Location $proj
try {
    & (Join-Path $gradle.FullName 'bin\gradle.bat') assembleDebug
    $apk = Join-Path $proj 'app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path $apk) {
        $dist = Join-Path $proj 'dist'
        New-Item -ItemType Directory -Force $dist | Out-Null
        Copy-Item $apk (Join-Path $dist 'superwaking-debug.apk') -Force
        $mb = [math]::Round((Get-Item $apk).Length / 1MB, 1)
        Write-Host "OK: $apk ($mb MB) -> dist\superwaking-debug.apk"
    } else {
        Write-Host "构建结束但没有找到 APK"
    }
} finally {
    Pop-Location
}
