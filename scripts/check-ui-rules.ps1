# 机械规则扫描器（无需 Gradle / 无需 JDK）
#
# 只机械校验「人类审查容易漏、CI 编译与单测都抓不到」的规则：
#
#   [错误] LAZY-ITEM-KEY           Lazy 列表项缺 key
#                                  → 官方：缺 key 会让重排时 item 的 remember 状态错乱（正确性）
#
#   [警告] LAZY-CONTENT-TYPE       Lazy 列表项缺 contentType
#                                  → 官方：「Consider adding contentType」属性能优化（"consider"），
#                                    且收益仅在**混合类型**列表中成立，故不阻断合并。
#                                    依据：developer.android.com/develop/ui/compose/lists
#
#   [错误] TEST-QUARANTINE         被 excludeTestsMatching 隔离的测试类未登记
#                                  → 未登记的隔离 = 静默失效的断言（CI 全绿但从不执行）。
#                                    登记位置：docs/testing.md「被隔离的测试」一节。
#
#   [错误] CONFLICT-MARKER         源文件残留合并冲突标记（.kt / .kts / .xml / .toml / .yml）
#                                  → 冲突标记被误提交后，编译错误会指向无关行号，排查成本极高。
#
#   [错误] ENUM-STATE-INFERENCE    mutableStateOf(EnumType.Member) 的类型推断陷阱
#                                  → Kotlin 会把期望类型解析成**枚举字面量类型**，之后赋同枚举的
#                                    其他成员即 `Assignment type mismatch`。本轮 issue #103 因此让
#                                    `:shared:compileTestKotlinJvm` 在 CI 上白跑 2 分钟。
#                                    判据：同一文件内该变量与初始成员不同的同类成员赋值。
#
#   [错误] ANDROID-RES-REF         Android 资源引用无法解析
#                                  → manifest / themes 里的 `@style|@color|@mipmap|@drawable|@xml|@string`
#                                    引用必须能在 `androidApp/src/main/res` 找到定义，
#                                    否则只有 AAPT link 阶段的晦涩错误。
#
#   [错误] EOF-NEWLINE             改动文件缺少文件末尾换行
#                                  → .editorconfig 的 insert_final_newline 只约束新写入，
#                                    历史文件靠 git diff --check 也兜不住；本规则只看**本次改动的文件**，
#                                    避免一次性炸出存量问题。
#
# 用法：
#   pwsh -File scripts/check-ui-rules.ps1           # 检查全库（有 warning 仍返回 0）
#   pwsh -File scripts/check-ui-rules.ps1 -Strict   # warning 也视为失败
#   pwsh -File scripts/check-ui-rules.ps1 -Verbose  # 打印扫描规模与 warning 明细
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

# ---------------------------------------------------------------- 规则 3：测试隔离登记
# 背景：build.gradle.kts 的 excludeTestsMatching 是隐形门禁——被排除的测试类不会执行，
# CI 依然是绿的。历史上正是靠偶然读到该文件，才发现收藏模块两个核心行为测试从未运行。
# 规则：每条 excludeTestsMatching 必须在 docs/testing.md 的隔离登记表里出现（按 pattern 子串匹配）。
$gradleFiles = @('shared/build.gradle.kts')
$testingDoc = 'docs/testing.md'
$quarantineRegistry = ''
if (Test-Path $testingDoc) {
    $quarantineRegistry = Get-Content -LiteralPath $testingDoc -Raw
}

foreach ($gradleFile in $gradleFiles) {
    if (-not (Test-Path $gradleFile)) { continue }
    $lines = Get-Content -LiteralPath $gradleFile
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $match = [regex]::Match($lines[$i], 'excludeTestsMatching\(\s*"([^"]+)"\s*\)')
        if (-not $match.Success) { continue }
        $pattern = $match.Groups[1].Value
        # 通配符去掉后再查登记表，避免 * 造成字面量匹配失败
        $needle = ($pattern -replace '\*', '')
        if ($quarantineRegistry -notlike "*$needle*") {
            $errors += [pscustomobject]@{
                File = $gradleFile; Line = $i + 1
                Text = "excludeTestsMatching(`"$pattern`")  ── 未在 docs/testing.md 登记"
                Hint = '在 docs/testing.md 的「被隔离的测试」表里补一行：pattern / 原因 / 恢复条件'
            }
        }
    }
}

# ---------------------------------------------------------------- 规则 4：冲突标记
# 冲突标记被误提交后，编译器报错位置与真实问题无关，且 IDE 折叠后极易漏看。
# 资源与配置（.xml / .toml / .yml）同样会被冲突标记污染，且 AAPT 的报错更晦涩，故一并扫描。
$codeFiles = git ls-files --cached --others --exclude-standard '*.kt' '*.kts' '*.xml' '*.toml' '*.yml' '*.yaml'
foreach ($file in $codeFiles) {
    $lines = Get-Content -LiteralPath $file
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*(<{7}|>{7})(\s|$)' -or $line -match '^\s*={7}\s*$') {
            $errors += [pscustomobject]@{
                File = $file; Line = $i + 1
                Text = "$($line.Trim())  ── 残留合并冲突标记"
                Hint = '解析冲突并删除 <<<<<<< / ======= / >>>>>>> 三行'
            }
        }
    }
}

# ---------------------------------------------------------------- 规则 5：枚举状态推断陷阱
# 背景：Kotlin 对 `mutableStateOf(EnumType.Member)` 的期望类型会解析成**枚举字面量类型**
# （EnumType.Member），而非枚举类型本身；之后 `state.value = EnumType.Other` 即
# `Assignment type mismatch`，只能等编译才发现。issue #103 的修正提交 77762b2 即此坑。
# 判据：同一文件内该变量被赋了「同枚举、不同成员」的字面量。
$stateFiles = git ls-files --cached --others --exclude-standard 'shared/src/**/*.kt' 'androidApp/src/**/*.kt' 'desktopApp/src/**/*.kt'
foreach ($file in $stateFiles) {
    $text = Get-Content -LiteralPath $file -Raw
    if ($text -notmatch 'mutableStateOf') { continue }

    # 形态 A：val/var x = mutableStateOf(Type.Member) → 赋值写作 `x.value = Type.Other`
    # 形态 B：var x by remember { mutableStateOf(Type.Member) } → 赋值写作 `x = Type.Other`
    $patterns = @(
        @{ Decl = '(?m)^\s*(?:var|val)\s+(\w+)\s*=\s*mutableStateOf\s*\(\s*([A-Z]\w*)\.([A-Z]\w*)\s*\)'; Assign = '(?m)^\s*{0}\s*\.\s*value\s*=\s*([A-Z]\w*)\.([A-Z]\w*)\s*$' },
        @{ Decl = '(?m)^\s*var\s+(\w+)\s+by\s+[^\n]*?mutableStateOf\s*\(\s*([A-Z]\w*)\.([A-Z]\w*)\s*\)'; Assign = '(?m)^\s*{0}\s*=\s*([A-Z]\w*)\.([A-Z]\w*)\s*$' }
    )
    foreach ($p in $patterns) {
        foreach ($decl in [regex]::Matches($text, $p.Decl)) {
            $name = $decl.Groups[1].Value
            $type = $decl.Groups[2].Value
            $member = $decl.Groups[3].Value
            $assignPattern = $p.Assign.Replace('{0}', [regex]::Escape($name))
            foreach ($assign in [regex]::Matches($text, $assignPattern)) {
                $assignType = $assign.Groups[1].Value
                $assignMember = $assign.Groups[2].Value
                if ($assignType -eq $type -and $assignMember -ne $member) {
                    $lineNo = ($text.Substring(0, $decl.Index) -split "`n").Count
                    $errors += [pscustomobject]@{
                        File = $file; Line = $lineNo
                        Text = "$name = mutableStateOf($type.$member) 后被赋 $assignType.$assignMember  ── 枚举字面量类型推断"
                        Hint = "显式声明泛型：mutableStateOf<$type>($type.$member)"
                    }
                }
            }
        }
    }
}

# ---------------------------------------------------------------- 规则 6：Android 资源引用可解析
# 背景：manifest 与 themes 里的 @style / @color / @mipmap / @drawable / @xml / @string 引用，
# 写错时只有 AAPT link 阶段的晦涩报错（且要等整个 :androidApp 编译）。
# 定义位置：同名文件（values*/xxx.xml）或 values 里的 <style name / <color name / <string name。
$androidResDir = 'androidApp/src/main/res'
if (Test-Path $androidResDir) {
    $resDefs = @{
        style   = New-Object System.Collections.Generic.HashSet[string]
        color   = New-Object System.Collections.Generic.HashSet[string]
        string  = New-Object System.Collections.Generic.HashSet[string]
        mipmap  = New-Object System.Collections.Generic.HashSet[string]
        drawable = New-Object System.Collections.Generic.HashSet[string]
        xml     = New-Object System.Collections.Generic.HashSet[string]
    }
    foreach ($resFile in (Get-ChildItem -LiteralPath $androidResDir -Recurse -File -Filter '*.xml')) {
        $relative = $resFile.FullName.Substring((Resolve-Path $androidResDir).Path.Length + 1) -replace '\\', '/'
        $parts = $relative -split '/'
        $dirName = $parts[0]
        $baseName = [System.IO.Path]::GetFileNameWithoutExtension($parts[-1])
        if ($dirName -match '^(mipmap|drawable|xml)') {
            if ($dirName -like 'mipmap*') { [void]$resDefs['mipmap'].Add($baseName) }
            elseif ($dirName -like 'drawable*') { [void]$resDefs['drawable'].Add($baseName) }
            else { [void]$resDefs['xml'].Add($baseName) }
        }
        if ($dirName -like 'values*') {
            $content = Get-Content -LiteralPath $resFile.FullName -Raw
            foreach ($m in [regex]::Matches($content, '<(?<tag>style|color|string)\s+name="(?<name>[^"]+)"')) {
                $tag = $m.Groups['tag'].Value
                $key = switch ($tag) { 'style' { 'style' } 'color' { 'color' } default { 'string' } }
                [void]$resDefs[$key].Add($m.Groups['name'].Value)
            }
        }
    }

    $refFiles = @('androidApp/src/main/AndroidManifest.xml') +
        (Get-ChildItem -LiteralPath $androidResDir -Recurse -File -Filter '*.xml' | ForEach-Object { $_.FullName })
    foreach ($refFile in $refFiles) {
        if (-not (Test-Path $refFile)) { continue }
        $lines = Get-Content -LiteralPath $refFile
        for ($i = 0; $i -lt $lines.Count; $i++) {
            foreach ($m in [regex]::Matches($lines[$i], '@(?<kind>style|color|string|mipmap|drawable|xml)/(?<name>[A-Za-z0-9_.]+)')) {
                $kind = $m.Groups['kind'].Value
                $name = $m.Groups['name'].Value
                if (-not $resDefs[$kind].Contains($name)) {
                    $displayFile = $refFile
                    if ($refFile.StartsWith($root)) { $displayFile = $refFile.Substring($root.Length + 1) -replace '\\', '/' }
                    $errors += [pscustomobject]@{
                        File = $displayFile; Line = $i + 1
                        Text = "@$kind/$name  ── 在 $androidResDir 中找不到定义"
                        Hint = '补对应资源（values 的 <style>/<color>/<string>，或 mipmap/drawable 同名文件），或修正引用名'
                    }
                }
            }
        }
    }
}

# ---------------------------------------------------------------- 规则 7：改动文件的末尾换行
# 背景：.editorconfig 的 insert_final_newline 只能约束新写入；历史文件缺失末尾换行时
# `git diff --check` 不会报（它只看改动行）。agent / IDE 一旦保存这些文件就会凭空多一行 diff。
# 范围收敛为**本分支相对 main 改动或新增的文件**，既覆盖新改动，又不会一次性炸出存量问题。
$baseRef = $null
if (Test-Path '.git') {
    git rev-parse --verify origin/main *> $null
    if ($LASTEXITCODE -eq 0) {
        $baseRef = 'origin/main'
    } else {
        git rev-parse --verify main *> $null
        if ($LASTEXITCODE -eq 0) { $baseRef = 'main' }
    }
}
if ($baseRef) {
    $changed = git diff --name-only --diff-filter=ACMR "$baseRef...HEAD"
    $changed += git diff --name-only --diff-filter=ACMR
    # 新增但尚未提交的文件不在任何 diff 里，恰恰是最容易出现缺末尾换行的一类
    $changed += git ls-files --others --exclude-standard
    foreach ($file in ($changed | Sort-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $file)) { continue }
        # 只关心文本类文件；二进制资源跳过
        if ($file -notmatch '\.(kt|kts|xml|toml|yml|yaml|md|properties|json)$') { continue }
        $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $file))
        if ($bytes.Length -eq 0) { continue }
        if ($bytes[-1] -ne 10) {
            $errors += [pscustomobject]@{
                File = $file; Line = 0
                Text = '缺少文件末尾换行'
                Hint = '按 .editorconfig insert_final_newline 补一个 LF 结尾'
            }
        }
    }
} elseif ($Verbose) {
    Write-Host '未能解析 main 分支，跳过 EOF-NEWLINE 检查' -ForegroundColor DarkGray
}

# ---------------------------------------------------------------- 输出
if ($Verbose) {
    Write-Host "扫描 $($uiFiles.Count) 个 UI 源文件、$($codeFiles.Count) 个代码/配置文件、$($stateFiles.Count) 个状态源文件"
}

$warningGroups = $warnings | Group-Object { ($_.Text -split '──')[-1].Trim() }
foreach ($group in $warningGroups) {
    if ($Verbose) {
        foreach ($w in $group.Group) {
            Write-Host "  警告 [LAZY-CONTENT-TYPE] $($w.File):$($w.Line)" -ForegroundColor Yellow
            Write-Host "    $($w.Text)"
            Write-Host "    -> $($w.Hint)" -ForegroundColor DarkGray
        }
    } else {
        Write-Host "  警告 [LAZY-CONTENT-TYPE] $($group.Count) 处$($group.Name)（-Verbose 查看明细）" -ForegroundColor Yellow
    }
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
        $tag = if ($e.Text -like '*缺 key*') { 'LAZY-ITEM-KEY' }
               elseif ($e.Text -like '*excludeTestsMatching*') { 'TEST-QUARANTINE' }
               elseif ($e.Text -like '*冲突标记*') { 'CONFLICT-MARKER' }
               elseif ($e.Text -like '*枚举字面量*') { 'ENUM-STATE-INFERENCE' }
               elseif ($e.Text -like '*找不到定义*') { 'ANDROID-RES-REF' }
               elseif ($e.Text -like '*末尾换行*') { 'EOF-NEWLINE' }
               else { 'CONFLICT-MARKER' }
        Write-Host "  [$tag] $($e.File):$($e.Line)" -ForegroundColor Red
        Write-Host "    $($e.Text)"
        Write-Host "    -> $($e.Hint)" -ForegroundColor Cyan
    }
}

Set-Location $PushDir
exit 1
