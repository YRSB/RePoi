$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
git config core.hooksPath .githooks
Write-Host '已启用仓库预检钩子：core.hooksPath = .githooks' -ForegroundColor Green
