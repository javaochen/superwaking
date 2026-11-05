param([switch]$Release)

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

$ver = Get-Content (Join-Path $proj 'version.properties') | ForEach-Object {
    if ($_ -match '^versionName=(.+)$') { $matches[1] }
} | Select-Object -First 1

Push-Location $proj
try {
    if ($Release) {
        & (Join-Path $gradle.FullName 'bin\gradle.bat') assembleRelease
        $apk = Join-Path $proj 'app\build\outputs\apk\release\app-release.apk'
        $name = "superwaking-$ver.apk"
    } else {
        & (Join-Path $gradle.FullName 'bin\gradle.bat') assembleDebug
        $apk = Join-Path $proj 'app\build\outputs\apk\debug\app-debug.apk'
        $name = 'superwaking-debug.apk'
    }

    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败（退出码 $LASTEXITCODE），不复制旧产物" }

    if (Test-Path $apk) {
        $dist = Join-Path $proj 'dist'
        New-Item -ItemType Directory -Force $dist | Out-Null
        $out = Join-Path $dist $name
        Copy-Item $apk $out -Force
        $mb = [math]::Round((Get-Item $apk).Length / 1MB, 2)
        Write-Host "OK: $out ($mb MB)"
    } else {
        Write-Host "构建结束但没有找到 APK"
    }
} finally {
    Pop-Location
}
