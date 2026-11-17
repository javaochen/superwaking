param([string]$Version, [int]$Code)

# 一键发布：改版本号 → release 构建 → 提交打 tag → 推送 → 建 GitHub Release
$ErrorActionPreference = 'Stop'
$proj = 'D:\works\app\superwaking'
Set-Location $proj

$vp = 'version.properties'
$cur = Get-Content $vp
$curName = (($cur | Where-Object { $_ -match '^versionName=' }) -replace '^versionName=', '')
$curCode = [int]((($cur | Where-Object { $_ -match '^versionCode=' }) -replace '^versionCode=', ''))

if (-not $Version) {
    $p = $curName -split '\.'
    $p[-1] = [string]([int]$p[-1] + 1)
    $Version = $p -join '.'
}
if (-not $Code) { $Code = $curCode + 1 }

"versionName=$Version`nversionCode=$Code" | Set-Content -Encoding ASCII $vp -NoNewline
Write-Host "版本 -> $Version (code $Code)"

& powershell -NoProfile -ExecutionPolicy Bypass -File "$proj\scripts\build.ps1" -Release
$apk = "$proj\dist\superwaking-$Version.apk"
if (-not (Test-Path $apk)) { throw "没有找到 $apk" }

git add version.properties
git -c user.name="openagent" -c user.email="agent@localhost" commit -q --allow-empty -m "release: v$Version ($Code)"
git tag "v$Version"

git push origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host "直连失败，走代理重试"
    git -c http.proxy=http://127.0.0.1:17890 push origin main
}
if ($LASTEXITCODE -ne 0) {
    throw "推送 main 失败：代理未运行且 github.com 不可达。恢复代理后重跑本脚本，或改用 SSH 通道。"
}

git push origin "v$Version"
if ($LASTEXITCODE -ne 0) {
    git -c http.proxy=http://127.0.0.1:17890 push origin "v$Version"
}
if ($LASTEXITCODE -ne 0) { throw "推送 tag v$Version 失败" }

gh release create "v$Version" $apk --title "v$Version" --notes "构建自 $((git rev-parse --short HEAD))"
if ($LASTEXITCODE -ne 0) { throw "创建 GitHub Release 失败（tag 已推送，可稍后手动 gh release create）" }

Write-Host "已发布：https://github.com/javaochen/superwaking/releases/tag/v$Version"
