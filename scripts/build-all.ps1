<#
.SYNOPSIS
    Motrix-Android 一键构建：toolchain -> renderer -> aria2 -> staging -> gradlew assembleDebug。

.DESCRIPTION
    串起 4 个子脚本 + 打包：
      1) scripts/install-toolchain.ps1        装 JDK17 + Android SDK/NDK（幂等）
      2) scripts/build-renderer.ps1           pnpm 构建并 stage renderer/server 进 assets
      3) scripts/fetch-aria2-android.ps1      取 android aria2c（arm64-v8a / armeabi-v7a）
      4) staging                              aria2c -> android\app\src\main\jniLibs\<abi>\libaria2c.so
      5) gradlew.bat assembleDebug            产出 app-debug.apk

    全程带错误检查与日志：日志写到 <工程根>\reports\build-all-<时间戳>.log。

.PARAMETER RepoRoot
    Motrix 源码仓库根（pnpm 构建用）。

.PARAMETER AndroidProject
    安卓 Gradle 工程目录（含 gradlew.bat 与 app/），默认 <工程根>\android。

.PARAMETER SkipToolchain
    跳过工具链安装（已装好时用，省时）。

.PARAMETER SkipAria2
    跳过 aria2 获取（已有二进制或走远程模式时用）。缺失时会在 staging 阶段告警。

.EXAMPLE
    pwsh ./scripts/build-all.ps1
    pwsh ./scripts/build-all.ps1 -SkipToolchain
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = 'C:\工作日志\projects\Motrix',
    [string]$AndroidProject,
    [switch]$SkipToolchain,
    [switch]$SkipAria2
)

$ErrorActionPreference = 'Stop'

# ---- 工程根与日志 -----------------------------------------------------------
$ProjectRoot = Split-Path -Parent $PSScriptRoot          # ...\motrix-android
if (-not $AndroidProject) { $AndroidProject = Join-Path $ProjectRoot 'android' }
$ScriptsDir  = Join-Path $ProjectRoot 'scripts'
$ReportsDir  = Join-Path $ProjectRoot 'reports'
New-Item -ItemType Directory -Force -Path $ReportsDir | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logPath = Join-Path $ReportsDir "build-all-$timestamp.log"

function Write-Step($m){ Write-Host "[build-all] $m" -ForegroundColor Cyan }
function Write-Warn2($m){ Write-Host "[build-all][WARN] $m" -ForegroundColor Yellow }

# 选宿主 powershell 可执行（用于以子进程方式调用子脚本，避免 child 的 exit 杀掉主流程）
$hostExe = if ($PSVersionTable.PSEdition -eq 'Core') { (Get-Process -Id $PID).Path } else { Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe' }

function Run-PsScript {
    param([string]$Path, [string[]]$ScriptArgs = @())
    if (-not (Test-Path -LiteralPath $Path)) { throw "子脚本不存在：$Path" }
    Write-Step ("运行 " + (Split-Path -Leaf $Path) + " " + ($ScriptArgs -join ' '))
    & $hostExe -NoProfile -ExecutionPolicy Bypass -File $Path @ScriptArgs 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path -Leaf $Path) 失败，退出码 $LASTEXITCODE" }
}

Start-Transcript -Path $logPath -Force | Out-Null
try {
    Write-Step "日志：$logPath"
    Write-Step "工程根：$ProjectRoot"
    Write-Step "仓库根：$RepoRoot"
    Write-Step "安卓工程：$AndroidProject"

    # ---- 1. 工具链 ----------------------------------------------------------
    if (-not $SkipToolchain) {
        Run-PsScript (Join-Path $ScriptsDir 'install-toolchain.ps1')
    } else { Write-Step '跳过工具链安装（-SkipToolchain）' }

    # ---- 2. renderer --------------------------------------------------------
    Run-PsScript (Join-Path $ScriptsDir 'build-renderer.ps1') -ScriptArgs @('-RepoRoot', $RepoRoot)

    # ---- 3. aria2 -----------------------------------------------------------
    $aria2Root = Join-Path $ProjectRoot 'third_party\aria2'
    if (-not $SkipAria2) {
        # fetch 脚本在缺件时 exit 2；用 try 包住但允许继续（远程模式仍可打包）
        try { Run-PsScript (Join-Path $ScriptsDir 'fetch-aria2-android.ps1') }
        catch { Write-Warn2 "aria2 获取未完全成功：$($_.Exception.Message)（若用远程模式可忽略；否则请人工取件）" }
    } else { Write-Step '跳过 aria2 获取（-SkipAria2）' }

    # ---- 4. staging aria2 -> jniLibs ---------------------------------------
    $jniRoot = Join-Path $AndroidProject 'app\src\main\jniLibs'
    $abiMap = @{ 'arm64-v8a'='arm64-v8a'; 'armeabi-v7a'='armeabi-v7a' }
    $staged = 0
    foreach ($abi in $abiMap.Keys) {
        $src = Join-Path $aria2Root "$abi\aria2c"
        if (Test-Path -LiteralPath $src) {
            $destDir = Join-Path $jniRoot $abi
            New-Item -ItemType Directory -Force -Path $destDir | Out-Null
            $dest = Join-Path $destDir 'libaria2c.so'    # 起 libxxx.so 名，Android 打包才会随 APK 解压到 nativeLibraryDir
            Copy-Item -LiteralPath $src -Destination $dest -Force
            Write-Step "staged $abi -> $dest"
            $staged++
        } else {
            Write-Warn2 "缺少 aria2c（$abi）：$src —— 跳过。内嵌模式(模式B)将无法下载，需先 fetch。"
        }
    }
    if ($staged -eq 0) { Write-Warn2 '未 stage 任何 aria2c；若目标为模式B(全内嵌)，打包后下载会失败。' }

    # ---- 5. gradle assembleDebug -------------------------------------------
    $gradlew = Join-Path $AndroidProject 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) {
        throw "未找到 gradlew.bat：$gradlew（安卓工程尚未就绪？见 BLUEPRINT 中 Lead 负责的 android/ 目录）"
    }
    Write-Step 'gradlew assembleDebug ...'
    Push-Location $AndroidProject
    try {
        & $gradlew assembleDebug --no-daemon 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "gradlew assembleDebug 失败，退出码 $LASTEXITCODE" }
    } finally { Pop-Location }

    $apk = Get-ChildItem -Path (Join-Path $AndroidProject 'app\build\outputs\apk') -Recurse -Filter '*.apk' -ErrorAction SilentlyContinue | Select-Object -First 1
    Write-Host ''
    Write-Host '==== 构建完成 ====' -ForegroundColor Green
    if ($apk) { Write-Host "  APK: $($apk.FullName)  ($([math]::Round($apk.Length/1MB,2)) MB)" -ForegroundColor Green }
    else { Write-Warn2 '未在 app\build\outputs\apk 找到 APK，请检查 gradle 输出。' }
    Write-Host "  日志：$logPath"
}
catch {
    Write-Host ''
    Write-Host "==== 构建中断：$($_.Exception.Message) ====" -ForegroundColor Red
    Write-Host "  详见日志：$logPath" -ForegroundColor Red
    exit 1
}
finally {
    try { Stop-Transcript | Out-Null } catch {}
}
exit 0
