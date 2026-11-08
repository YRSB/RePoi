# 静态规则检查（无需 Gradle / 无需 JDK）
#
# 只机械校验「人类审查容易漏、CI 编译与单测都抓不到」的规则：
#   1. Lazy 列表项缺 key / contentType（CODING_STANDARDS「列表渲染性能」）
#   2. PoiAsyncImage(adaptiveRatio = true) 同时被 fillMaxSize 锁死两轴
#      （此时 Modifier.aspectRatio 静默失效，图片会铺满容器）
#
# 用法：
#   pwsh -File scripts/check-ui-rules.ps1            # 检查全库
#   pwsh -File scripts/check-ui-rules.ps1 -Verbose   # 打印每个扫描项
#
# 退出码：0 = 通过；1 = 有违规。

param(
    [switch]$Verbose
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root

$violations = @()

# ---------------------------------------------------------------- 规则 1
# `items(N) {` 形式没有 key/contentType。骨架屏占位项同样适用。
# 允许的写法：items(count = N, key = { ... }, contentType = { ... }) { ... }
$uiFiles = git ls-files 'shared/src/commonMain/**/*.kt'
foreach ($file in $uiFiles) {
    $lines = Get-Content -LiteralPath $file
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*items\s*\(\s*\d+\s*\)') {
            $violations += [pscustomobject]@{
                Rule = 'LAZY-ITEM-KEY'
                File = $file
                Line = $i + 1
                Text = $line.Trim()
                Hint = '改为 items(count = N, key = { "<页面>_$it" }, contentType = { "<类型>" })'
            }
        }
        # `items(count = N)` 给了数量但没给 key/contentType（跨行写法）
        if ($line -match '^\s*items\s*\(\s*count\s*=' -and
            $line -notmatch 'key\s*=' -and
            $line -notmatch 'contentType\s*=') {
            $violations += [pscustomobject]@{
                Rule = 'LAZY-ITEM-KEY'
                File = $file
                Line = $i + 1
                Text = $line.Trim()
                Hint = '改为 items(count = N, key = { "<页面>_$it" }, contentType = { "<类型>" })'
            }
        }
    }
}

# ---------------------------------------------------------------- 规则 2（已移除）
# 曾尝试用文本启发式校验「PoiAsyncImage(adaptiveRatio = true) + fillMaxSize()」，
# 但 Compose 的 aspectRatio 是否生效取决于**父容器约束**：
#   - LazyRow / LazyColumn 项里，交叉轴无界，fillMaxSize() 在该轴是 no-op，aspectRatio 正常；
#   - 尺寸确定的父容器里，两轴都被锁死，aspectRatio 静默失效。
# 文本无法解析约束，实测全为假阳性，因此不在此处机械化。
# 该语义由 shared/src/jvmTest/.../PoiAsyncImageLayoutTest.kt 的 Compose UI 测试覆盖。

# ---------------------------------------------------------------- 输出
if ($Verbose) {
    Write-Host "扫描 $($uiFiles.Count) 个 UI 源文件"
}

if ($violations.Count -eq 0) {
    Write-Host "UI 规则检查通过（0 违规）" -ForegroundColor Green
    Pop-Location
    exit 0
}

Write-Host "发现 $($violations.Count) 处违规：" -ForegroundColor Red
foreach ($v in $violations) {
    Write-Host ""
    Write-Host "  [$($v.Rule)] $($v.File):$($v.Line)" -ForegroundColor Yellow
    Write-Host "    $($v.Text)"
    Write-Host "    → $($v.Hint)" -ForegroundColor Cyan
}
Pop-Location
exit 1
