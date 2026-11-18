<#
.SYNOPSIS
    Windows 下安装 Motrix-Android 构建工具链（幂等：已装则跳过）。

.DESCRIPTION
    安装/校验以下组件，并把环境变量写进【用户级】(User)：
      * JDK 17（Eclipse Temurin / Adoptium）
      * Android commandline-tools（sdkmanager）
      * platform-tools（adb）
      * build-tools;34.0.0
      * platforms;android-34
      * ndk;26.x（默认 26.3.11579264 = r26d）

    设置环境变量：ANDROID_HOME / ANDROID_SDK_ROOT / JAVA_HOME，
    并把 %ANDROID_HOME%\platform-tools、%ANDROID_HOME%\cmdline-tools\latest\bin、%JAVA_HOME%\bin 追加进用户 PATH。

    说明：本脚本会把 SDK/NDK/JDK 下载到本机（体积较大，NDK 约 1~1.5GB）。
    首次运行耗时较长；再次运行会检测已有安装并跳过。

.PARAMETER SdkRoot
    Android SDK 根目录，默认 %LOCALAPPDATA%\Android\Sdk。

.PARAMETER JdkRoot
    JDK 安装父目录，默认 C:\Program Files\Eclipse Adoptium。

.PARAMETER NdkVersion
    NDK 版本（sdkmanager 包名后缀），默认 26.3.11579264（r26d）。可改 26.1.10909125 / 26.2.x。

.PARAMETER CmdlineToolsUrl
    commandline-tools 的 Windows zip 下载地址（Google 官方）。Google 会更新最新版号，
    如默认地址 404，请到 https://developer.android.com/studio#command-line-tools-only 取最新链接替换。

.PARAMETER Force
    即使检测到已安装也强制重装对应组件。

.EXAMPLE
    pwsh ./scripts/install-toolchain.ps1
#>
[CmdletBinding()]
param(
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$JdkRoot = 'C:\Program Files\Eclipse Adoptium',
    [string]$NdkVersion = '26.3.11579264',
    [string]$CmdlineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Write-Step($m){ Write-Host "[toolchain] $m" -ForegroundColor Cyan }
function Write-Warn2($m){ Write-Host "[toolchain][WARN] $m" -ForegroundColor Yellow }

# ---- 0. 环境自检 ------------------------------------------------------------
if (-not $IsWindows -and $PSVersionTable.PSVersion.Major -ge 6) {
    throw '本脚本面向 Windows。请在 Windows PowerShell / pwsh 下运行。'
}
$arch = $env:PROCESSOR_ARCHITECTURE
if ($arch -ne 'AMD64') { Write-Warn2 "当前架构 $arch；下载的 JDK/cmdline-tools 按 x64 处理，非 x64 主机需自行调整。" }

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
}

# ---- 1. JDK 17 --------------------------------------------------------------
function Get-JdkMajor([string]$javaExe) {
    try {
        $out = & $javaExe -version 2>&1 | Out-String
        if ($out -match 'version "(\d+)') { return [int]$Matches[1] }
    } catch {}
    return $null
}

function Find-Jdk17 {
    # a) 现有 JAVA_HOME 是否就是 17
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        if ((Get-JdkMajor (Join-Path $env:JAVA_HOME 'bin\java.exe')) -eq 17) { return $env:JAVA_HOME }
    }
    # b) Adoptium 目录下找 jdk-17*
    if (Test-Path $JdkRoot) {
        $cand = Get-ChildItem -Path $JdkRoot -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like 'jdk-17*' } | Sort-Object Name -Descending | Select-Object -First 1
        if ($cand) {
            $j = Join-Path $cand.FullName 'bin\java.exe'
            if ((Test-Path $j) -and ((Get-JdkMajor $j) -eq 17)) { return $cand.FullName }
        }
    }
    return $null
}

$jdkHome = Find-Jdk17
if ($jdkHome -and -not $Force) {
    Write-Step "JDK17 已存在，跳过安装：$jdkHome"
} else {
    Write-Step '安装 JDK17（Temurin）...'
    $msi = Join-Path $env:TEMP 'temurin17-jdk-x64.msi'
    $jdkUrl = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk'
    Write-Step "下载：$jdkUrl"
    Invoke-WebRequest -Uri $jdkUrl -OutFile $msi -UseBasicParsing -TimeoutSec 900
    Write-Step '静默安装 JDK17（msiexec）...'
    $p = Start-Process msiexec.exe -ArgumentList @('/i', "`"$msi`"", '/qn', '/norestart', 'ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJavaHome') -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) { throw "JDK 安装失败，msiexec 退出码 $($p.ExitCode)" }
    Remove-Item $msi -Force -ErrorAction SilentlyContinue
    Refresh-Path
    $jdkHome = Find-Jdk17
    if (-not $jdkHome) { throw 'JDK17 安装后仍未能定位 java.exe，请手动核对安装目录。' }
    Write-Step "JDK17 安装完成：$jdkHome"
}

# ---- 2. Android commandline-tools ------------------------------------------
$sdkManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if ((Test-Path $sdkManager) -and -not $Force) {
    Write-Step "cmdline-tools 已存在，跳过：$sdkManager"
} else {
    Write-Step '安装 Android commandline-tools...'
    New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
    $zip = Join-Path $env:TEMP 'cmdline-tools.zip'
    Write-Step "下载：$CmdlineToolsUrl"
    Invoke-WebRequest -Uri $CmdlineToolsUrl -OutFile $zip -UseBasicParsing -TimeoutSec 900
    $tmp = Join-Path $env:TEMP ('cmdline_' + [guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $zip -DestinationPath $tmp -Force
    # zip 解出的是 cmdline-tools/ 目录；需放到 cmdline-tools/latest/
    $src = Join-Path $tmp 'cmdline-tools'
    if (-not (Test-Path $src)) { throw 'cmdline-tools zip 结构异常（应有 cmdline-tools/ 目录）' }
    $destParent = Join-Path $SdkRoot 'cmdline-tools'
    New-Item -ItemType Directory -Force -Path $destParent | Out-Null
    $latest = Join-Path $destParent 'latest'
    if (Test-Path $latest) { Remove-Item $latest -Recurse -Force }
    Move-Item -Path $src -Destination $latest
    Remove-Item $zip, $tmp -Recurse -Force -ErrorAction SilentlyContinue
    if (-not (Test-Path $sdkManager)) { throw "cmdline-tools 安装失败：未找到 $sdkManager" }
    Write-Step "cmdline-tools 安装完成：$sdkManager"
}

# 让 sdkmanager 使用我们刚装好的 JDK17
$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome 'bin') + ';' + $env:Path

# ---- 3. 许可 + 组件安装 -----------------------------------------------------
Write-Step '接受 Android SDK 许可...'
$yes = (1..30 | ForEach-Object { 'y' }) -join "`n"
$yes | & $sdkManager --sdk_root=$SdkRoot --licenses 2>&1 | Out-Host

$packages = @(
    'platform-tools',
    'build-tools;34.0.0',
    'platforms;android-34',
    "ndk;$NdkVersion"
)
Write-Step ("安装 SDK 组件：" + ($packages -join ', '))
& $sdkManager --sdk_root=$SdkRoot @packages 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw "sdkmanager 安装组件失败，退出码 $LASTEXITCODE" }

# 校验产物
$checks = @(
    (Join-Path $SdkRoot 'platform-tools\adb.exe'),
    (Join-Path $SdkRoot 'build-tools\34.0.0'),
    (Join-Path $SdkRoot 'platforms\android-34\android.jar'),
    (Join-Path $SdkRoot "ndk\$NdkVersion")
)
foreach ($c in $checks) {
    if (-not (Test-Path $c)) { Write-Warn2 "期望产物缺失：$c（可重跑本脚本或手动 sdkmanager 安装）" }
    else { Write-Step "OK: $c" }
}

# ---- 4. 持久化环境变量（用户级） -------------------------------------------
Write-Step '写入环境变量（用户级）...'
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkRoot, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $SdkRoot, 'User')
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdkHome, 'User')

# 追加 PATH（去重，避免重复追加）
$userPath = [Environment]::GetEnvironmentVariable('Path','User')
if (-not $userPath) { $userPath = '' }
$addPaths = @(
    (Join-Path $SdkRoot 'platform-tools'),
    (Join-Path $SdkRoot 'cmdline-tools\latest\bin'),
    (Join-Path $jdkHome 'bin')
)
$parts = @($userPath -split ';' | Where-Object { $_ -ne '' })
foreach ($ap in $addPaths) {
    if (-not ($parts | Where-Object { $_.TrimEnd('\') -ieq $ap.TrimEnd('\') })) { $parts += $ap }
}
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')

# 同步到当前会话，便于随后立即 build
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
Refresh-Path

Write-Host ''
Write-Host '==== 工具链就绪 ====' -ForegroundColor Green
Write-Host "  JAVA_HOME    = $jdkHome"
Write-Host "  ANDROID_HOME = $SdkRoot"
Write-Host "  NDK          = $SdkRoot\ndk\$NdkVersion"
Write-Host '提示：新开终端后环境变量才对新窗口生效；本窗口已同步。'
exit 0
