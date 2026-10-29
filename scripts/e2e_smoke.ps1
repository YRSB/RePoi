#Requires -Version 5.1
<#
.SYNOPSIS
  M7 E2E T0 smoke: boot check + APK install + launch + screencap on :16416.
.DESCRIPTION
  Worker uses ONLY `adb -s 127.0.0.1:16416` (device is parent-managed via
  mumu-cli; never invoke mumu-cli here). Fails fast on any step.
  APK path is passed in (CI artifact or local assembleDebug output) — the
  script never builds.
#>
param(
  [Parameter(Mandatory = $true)]
  [string]$ApkPath,
  [string]$Serial = "127.0.0.1:16416",
  [string]$OutDir = "e2e_out"
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
  param([string[]]$Args)
  & adb -s $Serial @Args
  if ($LASTEXITCODE -ne 0) { throw "adb $($Args -join ' ') failed ($LASTEXITCODE)" }
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$boot = (adb -s $Serial shell getprop sys.boot_completed) -join ""
if ($boot.Trim() -ne "1") { throw "device not booted (sys.boot_completed=$boot)" }
Write-Host "ok: device booted"

Invoke-Adb @("install", "-r", $ApkPath)
Write-Host "ok: installed $ApkPath"

$pkg = "com.example.repoi"
Invoke-Adb @("shell", "monkey", "-p", $pkg, "-c", "android.intent.category.LAUNCHER", "1")
Start-Sleep -Seconds 5

$snap = Join-Path $OutDir "t0_launch.png"
Invoke-Adb @("exec-out", "screencap", "-p") | Set-Content -Path $snap -Encoding Byte
if (-not (Test-Path $snap) -or (Get-Item $snap).Length -eq 0) { throw "empty screencap" }
Write-Host "ok: screencap $snap"

$fatal = (adb -s $Serial logcat -d | Select-String -Pattern "FATAL EXCEPTION" | Select-Object -First 5)
if ($fatal) { throw "FATAL in logcat:`n$($fatal -join "`n")" }
Write-Host "ok: no FATAL in logcat"
Write-Host "T0 SMOKE PASS"
