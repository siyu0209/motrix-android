<#
.SYNOPSIS
    构建 Motrix 的 renderer（前端静态站）与 server（Fastify），并 stage 进安卓 assets。

.DESCRIPTION
    在仓库根（默认 C:\工作日志\projects\Motrix）执行：
        pnpm install
        pnpm build:server        （产出 dist/renderer-web 与 dist/server）
    然后把：
        <RepoRoot>\dist\renderer-web   ->  <Assets>\renderer-web\
        <RepoRoot>\dist\server        ->  <Assets>\nodejs-project\dist-server\
    （<Assets> 默认 <motrix-android>\android\app\src\main\assets）

    只替换 renderer-web 与 nodejs-project\dist-server 两个子目录，
    不会动 nodejs-project 下其他文件（如 main.js）——那些是其他角色的产物。

.PARAMETER RepoRoot
    Motrix 源码仓库根目录（含 package.json 与 pnpm-workspace）。

.PARAMETER AssetsDir
    安卓 assets 目录。默认从本脚本位置推导 <motrix-android>\android\app\src\main\assets。

.PARAMETER SkipInstall
    跳过 pnpm install（依赖已装、只想重建时用）。

.EXAMPLE
    pwsh ./scripts/build-renderer.ps1
    pwsh ./scripts/build-renderer.ps1 -RepoRoot D:\src\Motrix -SkipInstall
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = 'C:\工作日志\projects\Motrix',
    [string]$AssetsDir,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
if (-not $AssetsDir) { $AssetsDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'android\app\src\main\assets' }

function Write-Step($m){ Write-Host "[renderer] $m" -ForegroundColor Cyan }
function Write-Warn2($m){ Write-Host "[renderer][WARN] $m" -ForegroundColor Yellow }

# ---- 0. 前置校验 ------------------------------------------------------------
if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot 'package.json'))) {
    throw "仓库根未找到 package.json：$RepoRoot（提示：仓库可能尚未解压/克隆完成，见 reports/build-attempt.md 的卡点说明）"
}
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { throw '未找到 node，请先安装 Node.js（建议 LTS）。' }

# 确保 pnpm 可用
function Ensure-Pnpm {
    if (Get-Command pnpm -ErrorAction SilentlyContinue) { return }
    Write-Warn2 '未找到 pnpm，尝试启用 corepack...'
    $corepack = Get-Command corepack -ErrorAction SilentlyContinue
    if ($corepack) {
        & corepack enable 2>&1 | Out-Host
        & corepack prepare pnpm@latest --activate 2>&1 | Out-Host
    }
    if (-not (Get-Command pnpm -ErrorAction SilentlyContinue)) {
        Write-Warn2 'corepack 不可用，改用 npm 全局安装 pnpm...'
        & npm install -g pnpm 2>&1 | Out-Host
    }
    if (-not (Get-Command pnpm -ErrorAction SilentlyContinue)) { throw 'pnpm 安装失败，请手动安装 pnpm 后重试。' }
}
Ensure-Pnpm

# ---- 1. 依赖安装 ------------------------------------------------------------
Push-Location $RepoRoot
try {
    if (-not $SkipInstall) {
        Write-Step 'pnpm install ...'
        & pnpm install 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "pnpm install 失败，退出码 $LASTEXITCODE" }
    } else {
        Write-Step '跳过 pnpm install（-SkipInstall）'
    }

    # ---- 2. 构建 server（含 renderer-web） ---------------------------------
    $pkg = Get-Content (Join-Path $RepoRoot 'package.json') -Raw | ConvertFrom-Json
    $hasBuildServer = $pkg.scripts.PSObject.Properties.Name -contains 'build:server'
    if ($hasBuildServer) {
        Write-Step 'pnpm build:server ...'
        & pnpm build:server 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "pnpm build:server 失败，退出码 $LASTEXITCODE" }
    } else {
        Write-Warn2 'package.json 无 build:server 脚本，尝试通用 pnpm build ...'
        & pnpm build 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "pnpm build 失败，退出码 $LASTEXITCODE" }
    }
} finally {
    Pop-Location
}

# ---- 3. 校验产物 ------------------------------------------------------------
$rendererSrc = Join-Path $RepoRoot 'dist\renderer-web'
$serverSrc   = Join-Path $RepoRoot 'dist\server'
foreach ($p in @($rendererSrc, $serverSrc)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "构建产物缺失：$p（检查构建是否成功，或脚本名是否为 build:server）" }
}
$rendererIndex = Join-Path $rendererSrc 'index.html'
if (-not (Test-Path $rendererIndex)) { Write-Warn2 "renderer-web 未见 index.html：$rendererSrc（可能入口名不同，仍继续拷贝）" }
$serverEntry = Join-Path $serverSrc 'index.mjs'
if (-not (Test-Path $serverEntry)) { Write-Warn2 "server 未见 index.mjs：$serverSrc（检查 dist/server 入口，仍继续拷贝）" }

# ---- 4. stage 进安卓 assets -------------------------------------------------
$destRenderer = Join-Path $AssetsDir 'renderer-web'
$destNodeProj = Join-Path $AssetsDir 'nodejs-project'
$destServer   = Join-Path $destNodeProj 'dist-server'

New-Item -ItemType Directory -Force -Path $AssetsDir | Out-Null

Write-Step "拷贝 renderer-web -> $destRenderer"
if (Test-Path -LiteralPath $destRenderer) { Remove-Item -LiteralPath $destRenderer -Recurse -Force }
Copy-Item -LiteralPath $rendererSrc -Destination $destRenderer -Recurse -Force

Write-Step "拷贝 dist/server -> $destServer"
New-Item -ItemType Directory -Force -Path $destNodeProj | Out-Null
if (Test-Path -LiteralPath $destServer) { Remove-Item -LiteralPath $destServer -Recurse -Force }
Copy-Item -LiteralPath $serverSrc -Destination $destServer -Recurse -Force

# ---- 5. 结果汇总 ------------------------------------------------------------
function Count-Files($p){ if (Test-Path $p) { (Get-ChildItem -LiteralPath $p -Recurse -File | Measure-Object).Count } else { 0 } }
$sz = { param($p) if (Test-Path $p) { [math]::Round(((Get-ChildItem -LiteralPath $p -Recurse -File | Measure-Object Length -Sum).Sum/1MB),2) } else { 0 } }

Write-Host ''
Write-Host '==== renderer 构建 + stage 完成 ====' -ForegroundColor Green
Write-Host ("  renderer-web : {0} 个文件, {1} MB -> {2}" -f (Count-Files $destRenderer), (& $sz $destRenderer), $destRenderer)
Write-Host ("  dist-server  : {0} 个文件, {1} MB -> {2}" -f (Count-Files $destServer), (& $sz $destServer), $destServer)
exit 0
