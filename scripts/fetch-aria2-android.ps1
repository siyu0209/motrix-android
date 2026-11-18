<#
.SYNOPSIS
    获取 Android 可用的 aria2c 可执行文件（arm64-v8a 与 armeabi-v7a 两套）。

.DESCRIPTION
    产物落地：
        <Root>/third_party/aria2/arm64-v8a/aria2c
        <Root>/third_party/aria2/armeabi-v7a/aria2c
        <Root>/third_party/aria2/manifest.json     （每个 abi 的 sha256 / 来源 / 是否已人工核对）
        <Root>/third_party/aria2/NOTICE.md         （来源与 GPLv2 许可证说明）

    说明（重要，勿删）：
      * aria2 官方 GitHub Release（aria2/aria2）只提供源码包与 Windows 构建，
        **不发布 Android 预编译二进制**。Android 可用的 aria2c 来自社区：
        首选 Termux 官方仓库的 deb 包（aarch64 = arm64-v8a，arm = armeabi-v7a）。
      * 社区/第三方二进制的 sha256 无法与官方签名自动对上，脚本记录实际哈希并标注
        verified=false，需人工复核（或用 -ExpectedSha256 传入已知值比对）。
      * 本脚本不发 “已校验” 的假声明；拿不到就如实失败并给出人工放置指引。

.PARAMETER Root
    工程根目录，默认取本脚本的上一级（motrix-android）。

.PARAMETER LocalArm64 / LocalArmv7
    可选：本地已备好的 aria2c（文件路径，或含 aria2c 的目录）。给了就优先用本地件，
    只做拷贝与记录，不联网。适合离线/已下载场景。

.PARAMETER Force
    已存在也重新获取/覆盖。

.PARAMETER ExpectedSha256
    可选：哈希表，如 @{ 'arm64-v8a'='<hex>'; 'armeabi-v7a'='<hex>' }，用于严格校验。

.EXAMPLE
    pwsh ./scripts/fetch-aria2-android.ps1
    pwsh ./scripts/fetch-aria2-android.ps1 -LocalArm64 .\aria2c-arm64 -LocalArmv7 .\aria2c-arm
#>
[CmdletBinding()]
param(
    [string]$Root,
    [string]$LocalArm64,
    [string]$LocalArmv7,
    [hashtable]$ExpectedSha256 = @{},
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # 加速 Invoke-WebRequest
if (-not $Root) { $Root = Split-Path -Parent $PSScriptRoot }   # $PSScriptRoot 在参数默认值阶段可能为空，故在体内解析

function Write-Step($m){ Write-Host "[aria2] $m" -ForegroundColor Cyan }
function Write-Warn2($m){ Write-Host "[aria2][WARN] $m" -ForegroundColor Yellow }
function Write-Err2($m){ Write-Host "[aria2][ERR] $m" -ForegroundColor Red }

$aria2Root = Join-Path $Root 'third_party\aria2'
New-Item -ItemType Directory -Force -Path $aria2Root | Out-Null

# ---- 候选下载源 -------------------------------------------------------------
# 每个 abi 给多条候选；kind: deb / direct / zip
# 说明：Termux deb 里的可执行文件在 data.tar.* 的 ./data/data/com.termux/files/usr/bin/aria2c
$candidates = @{
    'arm64-v8a' = @(
        @{ kind='deb'; url='https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/aria2_1.37.0_aarch64.deb'; note='Termux 官方 deb（aarch64）' },
        @{ kind='deb'; url='https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/aria2_1.36.0_aarch64.deb'; note='Termux 官方 deb（aarch64，旧版兜底）' },
        @{ kind='deb'; url='https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/'; note='Termux 目录页——需人工进目录挑最新 *_aarch64.deb' }
    )
    'armeabi-v7a' = @(
        @{ kind='deb'; url='https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/aria2_1.37.0_arm.deb'; note='Termux 官方 deb（arm）' },
        @{ kind='deb'; url='https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/aria2_1.36.0_arm.deb'; note='Termux 官方 deb（arm，旧版兜底）' },
        @{ kind='deb'; url='https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/'; note='Termux 目录页——需人工进目录挑最新 *_arm.deb' }
    )
}

# 社区参考源（不自动下载，仅用于人工取件；主机名可能变动，务必自行确认）
$communityHints = @(
    'https://github.com/aria2/aria2/releases        # 官方：只有源码/Windows，无 Android',
    'https://github.com/termux/termux-packages       # Termux 打包配方（可自行交叉编译）',
    'https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/  # 直接用 deb'
)

function Expand-Deb {
    param([string]$DebPath, [string]$WorkDir)
    # 解 .deb（ar 归档）-> data.tar.{xz,gz,zst} -> 解 tar
    $stage = Join-Path $WorkDir 'deb'
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    $tar = Get-Command tar -ErrorAction SilentlyContinue
    if (-not $tar) { throw '未找到 tar（Windows 10+ 自带 bsdtar）。请用 7-Zip 手动解压 deb。' }
    & tar -xf $DebPath -C $stage
    $dataTar = Get-ChildItem -LiteralPath $stage -Filter 'data.tar*' | Select-Object -First 1
    if (-not $dataTar) { throw "deb 内未找到 data.tar.*：$DebPath" }
    $dataOut = Join-Path $WorkDir 'data'
    New-Item -ItemType Directory -Force -Path $dataOut | Out-Null
    & tar -xf $dataTar.FullName -C $dataOut
    $found = Get-ChildItem -LiteralPath $dataOut -Recurse -File -Filter 'aria2c' | Select-Object -First 1
    if (-not $found) { throw "deb 解包后未找到 aria2c：$DebPath" }
    return $found.FullName
}

function Get-OneAria2 {
    param([string]$Abi, [string]$LocalPath)

    $destDir = Join-Path $aria2Root $Abi
    $destFile = Join-Path $destDir 'aria2c'
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null

    if ((Test-Path -LiteralPath $destFile) -and (-not $Force)) {
        Write-Step "$Abi 已存在，跳过（用 -Force 覆盖）：$destFile"
        return @{ abi=$Abi; path=$destFile; sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $destFile).Hash; source='(already present)'; verified=$false }
    }

    # 1) 本地件优先
    if ($LocalPath) {
        $resolved = $null
        if (Test-Path -LiteralPath $LocalPath -PathType Container) {
            $resolved = (Get-ChildItem -LiteralPath $LocalPath -Recurse -File -Filter 'aria2c' | Select-Object -First 1).FullName
        } elseif (Test-Path -LiteralPath $LocalPath) {
            $resolved = (Resolve-Path -LiteralPath $LocalPath).Path
        }
        if (-not $resolved) { throw "本地路径无效或其中无 aria2c：$LocalPath" }
        Copy-Item -LiteralPath $resolved -Destination $destFile -Force
        Write-Step "$Abi 使用本地件 -> $destFile"
        $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $destFile).Hash.ToLower()
        $ok = $ExpectedSha256.ContainsKey($Abi) -and ($ExpectedSha256[$Abi].ToLower() -eq $sha)
        return @{ abi=$Abi; path=$destFile; sha256=$sha; source="local:$resolved"; verified=[bool]$ok }
    }

    # 2) 联网逐条候选尝试
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("aria2fetch_" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    try {
        foreach ($c in $candidates[$Abi]) {
            if ($c.url.EndsWith('/')) { Write-Warn2 "$Abi 跳过目录页候选（需人工）：$($c.url)"; continue }
            try {
                $fileName = Split-Path -Leaf ([uri]$c.url).AbsolutePath
                $dl = Join-Path $tmp $fileName
                Write-Step "$Abi 下载：$($c.url)"
                Invoke-WebRequest -Uri $c.url -OutFile $dl -UseBasicParsing -TimeoutSec 120
                $bin = $null
                switch ($c.kind) {
                    'deb'    { $bin = Expand-Deb -DebPath $dl -WorkDir (Join-Path $tmp 'x') }
                    'zip'    { Expand-Archive -LiteralPath $dl -DestinationPath (Join-Path $tmp 'z') -Force; $bin = (Get-ChildItem -LiteralPath (Join-Path $tmp 'z') -Recurse -File -Filter 'aria2c' | Select-Object -First 1).FullName }
                    default  { $bin = $dl }
                }
                if (-not $bin) { throw '候选解出的二进制为空' }
                Copy-Item -LiteralPath $bin -Destination $destFile -Force
                $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $destFile).Hash.ToLower()
                $ok = $ExpectedSha256.ContainsKey($Abi) -and ($ExpectedSha256[$Abi].ToLower() -eq $sha)
                Write-Step "$Abi 完成：$destFile  sha256=$sha  verified=$ok"
                return @{ abi=$Abi; path=$destFile; sha256=$sha; source=$c.url; verified=[bool]$ok }
            } catch {
                Write-Warn2 "$Abi 候选失败（$($c.url)）：$($_.Exception.Message)"
            }
        }
    } finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Err2 "$Abi 全部候选失败。请人工取件后重跑：pwsh ./scripts/fetch-aria2-android.ps1 -LocalArm64 <arm64文件或目录> -LocalArmv7 <arm文件或目录>"
    return @{ abi=$Abi; path=$null; sha256=$null; source=$null; verified=$false }
}

# ---- 主流程 -----------------------------------------------------------------
Write-Step "工程根：$Root"
$results = @()
$results += Get-OneAria2 -Abi 'arm64-v8a'   -LocalPath $LocalArm64
$results += Get-OneAria2 -Abi 'armeabi-v7a' -LocalPath $LocalArmv7

# manifest.json（记录来源/哈希/核对状态，不臆造“已校验”）
$manifest = @{
    generatedAt = (Get-Date).ToString('s')
    generator   = 'scripts/fetch-aria2-android.ps1'
    license     = 'GPLv2（aria2）'
    note        = '社区/第三方二进制 sha256 无法与官方签名自动比对；verified=false 需人工复核。'
    artifacts   = @()
}
foreach ($r in $results) {
    $manifest.artifacts += [ordered]@{
        abi       = $r.abi
        file      = if ($r.path) { ($r.path -replace [regex]::Escape($Root+'\'), '') } else { $null }
        sha256    = $r.sha256
        source    = $r.source
        verified  = $r.verified
    }
}
$manifestPath = Join-Path $aria2Root 'manifest.json'
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
Write-Step "写入清单：$manifestPath"

# NOTICE.md（GPLv2 + 来源）；已存在则追加时间戳不覆盖
$noticePath = Join-Path $aria2Root 'NOTICE.md'
$notice = @"
# aria2（aria2c）第三方组件说明

本项目在 Android 端内嵌 **aria2** 命令行下载引擎（可执行文件 ``aria2c``）。

- 上游项目：https://github.com/aria2/aria2
- 许可证：**GNU General Public License v2.0 (GPLv2)**，全文见 https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
- 二进制来源：社区预编译（Termux 官方仓库 deb 优先），**非** aria2 官方发布（官方仅发源码/Windows 构建）。
- 合规要点：因为 aria2 为 GPLv2，随 App 分发 aria2 二进制时，需一并提供对应源码获取方式（或书面要约），
  且不得对 aria2 本身附加额外限制。本项目以独立进程方式调用 aria2c（通过 JSON-RPC/命令行），
  应用主体与 aria2 之间通过进程边界交互。

## 每个 ABI 的实际来源与哈希
见同目录 ``manifest.json``（由 fetch-aria2-android.ps1 生成）。

## 候选取件地址（供人工复核）
$($communityHints -join "`n")
"@
if (-not (Test-Path -LiteralPath $noticePath)) {
    $notice | Set-Content -LiteralPath $noticePath -Encoding UTF8
    Write-Step "写入 NOTICE：$noticePath"
} else {
    Add-Content -LiteralPath $noticePath -Value "`n<!-- 更新于 $((Get-Date).ToString('s')) by fetch-aria2-android.ps1 -->"
    Write-Step "NOTICE 已存在，追加更新时间：$noticePath"
}

$missing = $results | Where-Object { -not $_.path }
if ($missing.Count -gt 0) {
    Write-Err2 ("缺失 abi：" + (($missing | ForEach-Object { $_.abi }) -join ', '))
    exit 2
}

Write-Step "全部就位："
$results | ForEach-Object { Write-Host ("  {0,-12} {1}" -f $_.abi, $_.path) -ForegroundColor Green }
exit 0
