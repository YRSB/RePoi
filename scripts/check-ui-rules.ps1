# 静态规则检查（无需 Gradle / 无需 JDK）
#
# 只机械校验「人类审查容易漏、CI 编译与单测都抓不到」的规则：
#
#   [错误] LAZY-ITEM-KEY        Lazy 列表项缺 key
#                               → 官方：缺 key 会让重排时 item 的 remember 状态错乱（正确性）
#
#   [警告] LAZY-CONTENT-TYPE    Lazy 列表项缺 contentType
#                               → 官方：「Consider adding contentType」属性能优化（"consider"），
#                                 且收益仅在**混合类型**列表中成立，故不阻断合并。
#                                 依据：developer.android.com/develop/ui/compose/lists
#
# 用法：
#   pwsh -File scripts/check-ui-rules.ps1           # 检查全库（有 warning 仍返回 0）
#   pwsh -File scripts/check-ui-rules.ps1 -Strict   # warning 也视为失败
#   pwsh -File scripts/check-ui-rules.ps1 -Verbose  # 打印扫描规模
#
# 退出码：0 = 无错误（可能有警告）；1 = 有错误（或 -Strict 下有警告）。

param(
    [switch]$Verbose,
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
$PushDir = $PWD.Path
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$errors = @()
$warnings = @()

# 同时收录已跟踪与新增未提交文件：`git ls-files` 单独使用时不含 untracked，
# 会让「本地刚写的新文件」漏检（CI 检出后是新文件也已跟踪，但本地开发会漏）。
$uiFiles = git ls-files --cached --others --exclude-standard 'shared/src/commonMain/**/*.kt'

# 从 $lines[$start] 起收集一个调用的完整实参文本（按括号深度，最多 30 行）
function Get-CallArguments {
    param([string[]]$Lines, [int]$Start)
    $depth = 0
    $sb = [System.Text.StringBuilder]::new()
    $j = $Start
    for (; $j -lt [Math]::Min($Start + 30, $Lines.Count); $j++) {
        # 剥掉行注释（// 之后），避免注释里出现 key = 造成「已提供」误判
        $code = ($Lines[$j] -replace '//.*$', '')
        [void]$sb.AppendLine($code)
        # 括号深度忽略字符串字面量内的括号，避免 "(" 之类的文本打乱计数
        $stripped = [regex]::Replace($code, '"[^"]*"', '""')
        $depth += ([regex]::Matches($stripped, '\(')).Count
        $depth -= ([regex]::Matches($stripped, '\)')).Count
        if ($depth -le 0) { break }
    }
    return @{ Args = $sb.ToString(); EndLine = $j }
}

<#
  判断实参文本里是否**真的提供了**某个命名参数。

  只匹配命名参数形态 `name =`，且要求它出现在括号内、不在字符串字面量里：
  先把所有字符串字面量替换成空串再匹配，这样
  `contentType = { "has key = inside" }` 不会被误认为提供了 key。
#>
function Test-NamedArgument {
    param([string]$ArgText, [string]$ArgName)
    $withoutStrings = [regex]::Replace($ArgText, '"[^"]*"', '""')
    return $withoutStrings -match "\b$([regex]::Escape($ArgName))\s*="
}

foreach ($file in $uiFiles) {
    $lines = Get-Content -LiteralPath $file
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]

        # 大小写敏感匹配，排除枚举成员 `ITEMS(...)`
        if ($line -cnotmatch '\bitems\s*\(') { continue }
        # itemsIndexed / itemsIndexed(list) 的 index 语义另有约束，且 key 常省，跳过
        if ($line -match '\bitemsIndexed\s*\(') { continue }

        $call = Get-CallArguments -Lines $lines -Start $i
        $argText = $call.Args
        if ($argText -notmatch '\bitems\s*\(') { continue }

        $hasKey = Test-NamedArgument -ArgText $argText -ArgName 'key'
        $hasContentType = Test-NamedArgument -ArgText $argText -ArgName 'contentType'

        if (-not $hasKey) {
            $errors += [pscustomobject]@{
                File = $file; Line = $i + 1
                Text = "$($line.Trim())  ── 缺 key"
                Hint = 'key = { 稳定唯一值 }，缺失会让重排时 item 的 remember 状态错乱'
            }
        }
        if (-not $hasContentType) {
            $warnings += [pscustomobject]@{
                File = $file; Line = $i + 1
                Text = "$($line.Trim())  ── 缺 contentType"
                Hint = '混合类型列表建议补 contentType = { "类型名" }；同质列表可忽略'
            }
        }
    }
}

# ---------------------------------------------------------------- 规则 2（已移除）
# 曾尝试用文本启发式校验「PoiAsyncImage(adaptiveRatio = true) + fillMaxSize()」，
# 但 aspectRatio 是否生效取决于**父容器约束**：
#   - LazyRow / LazyColumn 项里，交叉轴无界，fillMaxSize() 在该轴是 no-op，aspectRatio 正常；
#   - 尺寸确定的父容器里，两轴都被锁死，aspectRatio 静默失效。
# 文本无法解析约束，实测全为假阳性，故不做机械化。
# 该语义由 shared/src/jvmTest/.../PoiAsyncImageLayoutTest.kt 的 Compose UI 测试覆盖。

# ---------------------------------------------------------------- 输出
if ($Verbose) {
    Write-Host "扫描 $($uiFiles.Count) 个 UI 源文件"
}

foreach ($w in $warnings) {
    Write-Host "  警告 [LAZY-CONTENT-TYPE] $($w.File):$($w.Line)" -ForegroundColor Yellow
    Write-Host "    $($w.Text)"
    Write-Host "    -> $($w.Hint)" -ForegroundColor DarkGray
}

if ($errors.Count -eq 0 -and ($warnings.Count -eq 0 -or -not $Strict)) {
    if ($warnings.Count -gt 0) {
        Write-Host "UI 规则检查通过（0 错误，$($warnings.Count) 警告）" -ForegroundColor Green
    } else {
        Write-Host "UI 规则检查通过（0 违规）" -ForegroundColor Green
    }
    Set-Location $PushDir
    exit 0
}

if ($errors.Count -gt 0) {
    Write-Host ""
    Write-Host "发现 $($errors.Count) 处错误：" -ForegroundColor Red
    foreach ($e in $errors) {
        Write-Host "  [LAZY-ITEM-KEY] $($e.File):$($e.Line)" -ForegroundColor Red
        Write-Host "    $($e.Text)"
        Write-Host "    -> $($e.Hint)" -ForegroundColor Cyan
    }
}

Set-Location $PushDir
exit 1
