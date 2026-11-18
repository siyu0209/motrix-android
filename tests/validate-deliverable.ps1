<#
.SYNOPSIS
    Motrix 安卓移植 — 交付物完整性校验（纯 PowerShell，可重复运行）。

.DESCRIPTION
    扫描交付树，校验关键文件是否齐全：BLUEPRINT、docs 7 份、android 工程关键文件、
    scripts 4 个、renderer-overrides、third_party/aria2/NOTICE.md、assets 目录、tests 自身。
    逐项输出 PASS / FAIL / WARN，末尾汇总缺失项；全部必需项通过时退出码为 0，否则为 1。
    脚本无副作用、不修改任何文件（除非显式传 -ReportPath 写报告），可反复运行。

.PARAMETER Root
    项目根目录。缺省取本脚本所在目录（tests\）的上一级，即 motrix-android 项目根。

.PARAMETER ReportPath
    可选。将校验结果另存为 Markdown 报告（如 .\reports\deliverable-check.md）。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\tests\validate-deliverable.ps1

.EXAMPLE
    pwsh -File .\tests\validate-deliverable.ps1 -Root "C:\工作日志\projects\motrix-android" -ReportPath .\reports\deliverable-check.md
#>
[CmdletBinding()]
param(
    [string]$Root,
    [string]$ReportPath
)

$ErrorActionPreference = 'Stop'

# ---- 解析根目录（可移植：默认脚本上级；也允许显式传入）----
if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Split-Path -Parent $PSScriptRoot
} else {
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        Write-Host "[ERROR] 根目录不存在: $Root" -ForegroundColor Red
        exit 2
    }
    $Root = (Resolve-Path -LiteralPath $Root).Path
}

$results = New-Object System.Collections.Generic.List[object]

function Add-Raw {
    param([string]$Group, [string]$Item, [string]$RelPath, [string]$Kind, [bool]$Required, [string]$Status, [string]$Note = '')
    $results.Add([pscustomobject]@{
        Group    = $Group
        Item     = $Item
        Path     = $RelPath
        Kind     = $Kind
        Required = $Required
        Status   = $Status
        Note     = $Note
    })
}

function Add-Check {
    param(
        [string]$Group,
        [string]$Item,
        [string]$RelPath,
        [ValidateSet('File', 'Dir', 'Any')][string]$Kind = 'File',
        [bool]$Required = $true,
        [bool]$NonEmpty = $false
    )
    $full = Join-Path $Root $RelPath
    $exists = $false
    $note = ''
    switch ($Kind) {
        'File' { $exists = Test-Path -LiteralPath $full -PathType Leaf }
        'Dir'  { $exists = Test-Path -LiteralPath $full -PathType Container }
        'Any'  { $exists = Test-Path -LiteralPath $full }
    }
    if ($exists -and $Kind -eq 'Dir' -and $NonEmpty) {
        $child = @(Get-ChildItem -LiteralPath $full -Force -ErrorAction SilentlyContinue)
        if ($child.Count -eq 0) { $exists = $false; $note = '目录存在但为空' }
    }
    $status = if ($exists) { 'PASS' } elseif ($Required) { 'FAIL' } else { 'WARN' }
    Add-Raw -Group $Group -Item $Item -RelPath $RelPath -Kind $Kind -Required $Required -Status $status -Note $note
}

# =====================================================================
# 1) 根文件
# =====================================================================
Add-Check '根文件' '总蓝图 BLUEPRINT' 'BLUEPRINT.md' File $true
Add-Check '根文件' '项目 README' 'README.md' File $false

# =====================================================================
# 2) docs 7 份
# =====================================================================
$docNames = @(
    '00-需求转译.md',
    '01-源码审计.md',
    '02-架构方案.md',
    '03-构建指南.md',
    '04-差距与路线图.md',
    '05-交付清单.md',
    '06-QA测试计划.md'
)
foreach ($d in $docNames) {
    Add-Check 'docs (7 份)' $d ("docs/" + $d) File $true
}

# =====================================================================
# 3) android 工程关键文件
# =====================================================================
Add-Check 'android 工程' 'settings.gradle.kts' 'android/settings.gradle.kts' File $true
Add-Check 'android 工程' 'build.gradle.kts' 'android/build.gradle.kts' File $true
Add-Check 'android 工程' 'gradle.properties' 'android/gradle.properties' File $true
Add-Check 'android 工程' 'app/build.gradle.kts' 'android/app/build.gradle.kts' File $true
Add-Check 'android 工程' 'AndroidManifest.xml' 'android/app/src/main/AndroidManifest.xml' File $true
Add-Check 'android 工程' 'java 源目录' 'android/app/src/main/java' Dir $true
Add-Check 'android 工程' 'res 资源目录' 'android/app/src/main/res' Dir $true
Add-Check 'android 工程' 'assets 目录' 'android/app/src/main/assets' Dir $true
Add-Check 'android 工程' 'assets/renderer-web' 'android/app/src/main/assets/renderer-web' Dir $false
Add-Check 'android 工程' 'assets/nodejs-project' 'android/app/src/main/assets/nodejs-project' Dir $true
Add-Check 'android 工程' 'nodejs 启动脚本 main.js' 'android/app/src/main/assets/nodejs-project/main.js' File $false

# 至少存在一个 Java/Kotlin 源文件（MainActivity 等）
$javaDir = Join-Path $Root 'android/app/src/main/java'
$srcFound = $false
if (Test-Path -LiteralPath $javaDir -PathType Container) {
    $src = Get-ChildItem -LiteralPath $javaDir -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in @('.java', '.kt') } | Select-Object -First 1
    if ($null -ne $src) { $srcFound = $true }
}
Add-Raw -Group 'android 工程' -Item 'Java/Kotlin 源文件(≥1)' -RelPath 'android/app/src/main/java/**/*.java|*.kt' -Kind 'File' `
    -Required $true -Status ($(if ($srcFound) { 'PASS' } else { 'FAIL' }))

# =====================================================================
# 4) scripts 4 个
# =====================================================================
$scriptNames = @(
    'install-toolchain.ps1',
    'fetch-aria2-android.ps1',
    'build-renderer.ps1',
    'build-all.ps1'
)
foreach ($s in $scriptNames) {
    Add-Check 'scripts (4 个)' $s ("scripts/" + $s) File $true
}

# =====================================================================
# 5) renderer-overrides
# =====================================================================
Add-Check 'renderer-overrides' '目录(非空)' 'renderer-overrides' Dir $true -NonEmpty $true
foreach ($f in @('mobile-adapt.css', 'mobile-adapt.js', 'README.md', 'screens.md')) {
    Add-Check 'renderer-overrides' $f ("renderer-overrides/" + $f) File $true
}

# =====================================================================
# 6) third_party/aria2
# =====================================================================
Add-Check 'third_party/aria2' 'aria2 目录' 'third_party/aria2' Dir $true
Add-Check 'third_party/aria2' 'NOTICE.md' 'third_party/aria2/NOTICE.md' File $true

# =====================================================================
# 7) tests 自身（自检，可选）
# =====================================================================
Add-Check 'tests (自身)' 'validate-deliverable.ps1' 'tests/validate-deliverable.ps1' File $false
Add-Check 'tests (自身)' 'tests/README.md' 'tests/README.md' File $false

# =====================================================================
# 输出
# =====================================================================
$groupOrder = @('根文件', 'docs (7 份)', 'android 工程', 'scripts (4 个)', 'renderer-overrides', 'third_party/aria2', 'tests (自身)')

$fail = @($results | Where-Object { $_.Status -eq 'FAIL' })
$warn = @($results | Where-Object { $_.Status -eq 'WARN' })
$pass = @($results | Where-Object { $_.Status -eq 'PASS' })
$total = $results.Count

$line = '=' * 66
Write-Host $line -ForegroundColor Cyan
Write-Host ' Motrix 安卓移植 — 交付物完整性校验' -ForegroundColor Cyan
Write-Host $line -ForegroundColor Cyan
Write-Host (" Root : {0}" -f $Root)
Write-Host (" 时间 : {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Host (" 环境 : PowerShell {0}" -f $PSVersionTable.PSVersion.ToString())
Write-Host ''

foreach ($g in $groupOrder) {
    $rows = @($results | Where-Object { $_.Group -eq $g })
    if ($rows.Count -eq 0) { continue }
    Write-Host ("[{0}]" -f $g) -ForegroundColor Cyan
    foreach ($r in $rows) {
        $color = switch ($r.Status) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
        $suffix = ''
        if ($r.Note) { $suffix = '  <- ' + $r.Note }
        $reqMark = if ($r.Required) { '' } else { ' (可选)' }
        Write-Host ("  {0,-4} {1,-34} {2}{3}{4}" -f $r.Status, $r.Item, $r.Path, $reqMark, $suffix) -ForegroundColor $color
    }
    Write-Host ''
}

Write-Host $line -ForegroundColor Cyan
Write-Host (" 汇总：总计 {0} 项 | PASS {1} | FAIL {2} | WARN {3}" -f $total, $pass.Count, $fail.Count, $warn.Count)
if ($fail.Count -gt 0) {
    Write-Host ''
    Write-Host ' 缺失项（必须补齐，否则判定 FAIL）：' -ForegroundColor Red
    foreach ($r in $fail) {
        Write-Host ("   - [{0}] {1}  ({2})" -f $r.Group, $r.Item, $r.Path) -ForegroundColor Red
    }
}
if ($warn.Count -gt 0) {
    Write-Host ''
    Write-Host ' 提示项（可选，建议补齐）：' -ForegroundColor Yellow
    foreach ($r in $warn) {
        Write-Host ("   - [{0}] {1}  ({2})" -f $r.Group, $r.Item, $r.Path) -ForegroundColor Yellow
    }
}

$overall = if ($fail.Count -eq 0) { 'PASS' } else { 'FAIL' }
Write-Host ''
if ($overall -eq 'PASS') {
    Write-Host (" RESULT: {0} —— 关键交付物齐全，可进入功能测试。" -f $overall) -ForegroundColor Green
} else {
    Write-Host (" RESULT: {0} —— 有 {1} 项必需交付物缺失，请补齐后重跑。" -f $overall, $fail.Count) -ForegroundColor Red
}
Write-Host $line -ForegroundColor Cyan

# ---- 可选：写 Markdown 报告 ----
if (-not [string]::IsNullOrWhiteSpace($ReportPath)) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('# Motrix 安卓移植 — 交付物完整性校验报告')
    [void]$sb.AppendLine('')
    [void]$sb.AppendLine(('- 根目录：`{0}`' -f $Root))
    [void]$sb.AppendLine(('- 生成时间：{0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')))
    [void]$sb.AppendLine(('- 环境：PowerShell {0}' -f $PSVersionTable.PSVersion.ToString()))
    [void]$sb.AppendLine(('- 结论：**{0}**（总计 {1} | PASS {2} | FAIL {3} | WARN {4}）' -f $overall, $total, $pass.Count, $fail.Count, $warn.Count))
    [void]$sb.AppendLine('')
    [void]$sb.AppendLine('| 分组 | 项 | 路径 | 必需 | 状态 | 备注 |')
    [void]$sb.AppendLine('|---|---|---|---|---|---|')
    foreach ($g in $groupOrder) {
        foreach ($r in @($results | Where-Object { $_.Group -eq $g })) {
            $req = if ($r.Required) { '是' } else { '否' }
            [void]$sb.AppendLine(('| {0} | {1} | `{2}` | {3} | {4} | {5} |' -f $r.Group, $r.Item, $r.Path, $req, $r.Status, $r.Note))
        }
    }
    if ($fail.Count -gt 0) {
        [void]$sb.AppendLine('')
        [void]$sb.AppendLine('## 缺失项（必须补齐）')
        foreach ($r in $fail) { [void]$sb.AppendLine(('- [ ] [{0}] {1}（`{2}`）' -f $r.Group, $r.Item, $r.Path)) }
    }
    $reportFull = if ([System.IO.Path]::IsPathRooted($ReportPath)) { $ReportPath } else { Join-Path (Get-Location).Path $ReportPath }
    $reportDir = Split-Path -Parent $reportFull
    if ($reportDir -and -not (Test-Path -LiteralPath $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
    $sb.ToString() | Out-File -LiteralPath $reportFull -Encoding UTF8
    Write-Host (" 报告已写出：{0}" -f $reportFull) -ForegroundColor Cyan
}

if ($overall -eq 'PASS') { exit 0 } else { exit 1 }
