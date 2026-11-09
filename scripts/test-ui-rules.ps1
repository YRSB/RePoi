# 脚本边缘场景验证夹具
# 每个用例在临时文件里构造，跑脚本后断言结果，不污染仓库。
$ErrorActionPreference = 'Stop'
$repo = 'c:\Users\nobody\AndroidReverse\RePoi'
$probeDir = Join-Path $repo 'shared/src/commonMain/kotlin/com/nobody/repoi/ui/zzprobe'
$script = Join-Path $repo 'scripts/check-ui-rules.ps1'

function Run-Case {
    param([string]$Name, [string]$Body, [int]$ExpectErrors, [int]$ExpectWarnings)
    if (Test-Path $probeDir) { Remove-Item $probeDir -Recurse -Force }
    New-Item -ItemType Directory -Path $probeDir -Force | Out-Null
    $f = Join-Path $probeDir 'Probe.kt'
    $src = @"
package com.nobody.repoi.ui.zzprobe

import androidx.compose.foundation.lazy.LazyColumn

fun Probe() {
    LazyColumn {
$Body
    }
}
"@
    [System.IO.File]::WriteAllText($f, $src)
    $out = & pwsh -File $script 2>&1 | Out-String
    $code = $LASTEXITCODE
    $errCount = ([regex]::Matches($out, '\[LAZY-ITEM-KEY\] [^:]*zzprobe')).Count
    $warnCount = ([regex]::Matches($out, '\[LAZY-CONTENT-TYPE\] [^:]*zzprobe')).Count
    $ok = ($errCount -eq $ExpectErrors) -and ($warnCount -eq $ExpectWarnings) -and
          ($code -eq $(if ($ExpectErrors -gt 0) { 1 } else { 0 }))
    $status = if ($ok) { 'PASS' } else { 'FAIL' }
    $detail = "err=$errCount(want $ExpectErrors) warn=$warnCount(want $ExpectWarnings) exit=$code"
    Write-Host ("{0,-6} {1,-52} {2}" -f $status, $Name, $detail) -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
    if (-not $ok) { Write-Host $out }
}

Write-Host "=== 边缘场景测试 ===" -ForegroundColor Cyan

Run-Case '1 基线：key+contentType 齐全' @'
        items(items = listOf(1), key = { it }, contentType = { "a" }) { }
'@ 0 0

Run-Case '2 缺 key（应报错误）' @'
        items(items = listOf(1), contentType = { "a" }) { }
'@ 1 0

Run-Case '3 缺 contentType（应报警告）' @'
        items(items = listOf(1), key = { it }) { }
'@ 0 1

Run-Case '4 两者都缺（1 错误 + 1 警告）' @'
        items(listOf(1)) { }
'@ 1 1

Run-Case '5 数量形式 items(6) 缺两者' @'
        items(6) { }
'@ 1 1

Run-Case '6 跨行齐全（不应误报）' @'
        items(
            items = listOf(1),
            key = { it },
            contentType = { "a" }
        ) { }
'@ 0 0

Run-Case '7 注释中出现 key=（不应算作已提供）' @'
        items(items = listOf(1)) { } // key = { it }, contentType = { "a" }
'@ 1 1

Run-Case '8 字符串字面量里含 key=（不应算作已提供）' @'
        items(items = listOf(1), contentType = { "has key = inside" }) { }
'@ 1 0

Run-Case '9 枚举成员 ITEMS(...)（不应误报）' @'
        val x = ITEMS("a", "b")
'@ 0 0

Run-Case '10 itemsIndexed（跳过，不报）' @'
        itemsIndexed(listOf(1)) { i, v -> }
'@ 0 0

Run-Case '11 key 换行后才出现' @'
        items(
            items = listOf(1),
            key = {
                it
            },
            contentType = { "a" }
        ) { }
'@ 0 0

Run-Case '12 嵌套 items（内外各缺 contentType）' @'
        items(items = listOf(1), key = { it }) { }
        items(items = listOf(2), key = { it }) { }
'@ 0 2

Run-Case '13 单行 items(6, key=, contentType=) 齐全' @'
        items(6, key = { it }, contentType = { "a" }) { }
'@ 0 0

Run-Case '14 key 用命名参数但写成 lambda 多行' @'
        items(
            items = listOf(1),
            contentType = { "a" }
        ) { }
'@ 1 0

if (Test-Path $probeDir) { Remove-Item $probeDir -Recurse -Force }
Write-Host ""
Write-Host "=== 清理完成 ===" -ForegroundColor Cyan
