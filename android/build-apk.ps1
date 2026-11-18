# 一键构建 Motrix Android APK
# 用法: pwsh -File build-apk.ps1 [-Flavor remote|embedded] [-Task assembleDebug|assembleRelease]
param(
    [string]$Flavor = "remote",
    [string]$Task   = "assembleDebug"
)
$ErrorActionPreference = 'Stop'
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path "$jdk\bin\java.exe")) {
    $jdk = "C:\Users\zhoutao\AppData\Local\Programs\rea-deps\jdk-21.0.13+9"
}
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "C:\Users\zhoutao\AppData\Local\Android\Sdk" }
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:Path = "$jdk\bin;$sdk\platform-tools;$env:Path"

Set-Location $PSScriptRoot
# 本会话已备 gradle 8.7；否则提示安装
$gradle = "C:\工作日志\projects\tools\gradle-8.7\bin\gradle.bat"
if (-not (Test-Path $gradle)) {
    if (Test-Path ".\gradlew.bat") { $gradle = ".\gradlew.bat" }
    else { throw "未找到 gradle：请先运行 gradle wrapper 生成 gradlew，或安装 Gradle 8.7" }
}
$gTask = if ($Task -eq "assembleRelease") { "assemble" + ($Flavor.Substring(0,1).ToUpper() + $Flavor.Substring(1)) + "Release" } else { "assemble" + ($Flavor.Substring(0,1).ToUpper() + $Flavor.Substring(1)) + "Debug" }
Write-Host "==> $gradle $gTask (JAVA_HOME=$jdk)"
& $gradle $gTask --no-daemon --stacktrace --console=plain
if ($LASTEXITCODE -ne 0) { throw "构建失败，退出码 $LASTEXITCODE" }
$apk = Get-ChildItem -Recurse -Filter "app-$Flavor-*.apk" -Path ".\app\build\outputs" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($apk) { Write-Host "==> APK: $($apk.FullName) ($([math]::Round($apk.Length/1MB,2)) MB)" } else { Write-Warning "未找到 APK 产物" }
