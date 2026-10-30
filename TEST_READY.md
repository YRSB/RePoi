# TEST_READY.md — M7 release readiness checklist (KMP migration close-out)

PR: `kmp/m7-final` → `main` (PR #15). Device: `127.0.0.1:16416`
(MuMu vmindex 1, Android 15, 1440x2560 portrait baseline; landscape via `wm`).

## 1. Unit-green (CI-gating)

- [ ] `:shared:jvmTest` GREEN on PR head.
- [ ] `:shared:testAndroidHostTest` GREEN on PR head.
- [ ] Quarantine filter active (`*ViewModel*Test` excluded; see TEST_INFRA §3).
- [ ] Known-flaky watch: `TokenManagerTest:21` (DataStore timing; green on re-run).

## 2. Dual-compile + artifacts

- [ ] `:androidApp:assembleDebug` → `android-debug-apk` artifact (non-empty).
- [ ] `:desktopApp:packageDistributionForCurrentOS` (linux + windows).
- [ ] `:desktopApp:createDistributable` + `packageMsi` (windows) →
      `binaries/main/msi/*.msi` + runnable `*.exe` (non-empty).

## 3. E2E entry criteria (device)

- [ ] `adb -s 127.0.0.1:16416 shell getprop sys.boot_completed` → `1`.
- [ ] `scripts/e2e_smoke.ps1` (T0) passes: install + launch + screencap.
- [ ] E2E slice (F1/F4/F39) per `docs/E2E_MATRIX.md`, then full matrix.
- [ ] `logcat` FATAL scan clean, portrait + landscape walkthrough.
- [ ] Device drop mid-E2E → STOP-report, no faked evidence.

## 4. Reproducible commands

```powershell
gh pr view 15 --json headRefOid,state --jq '{oid:.headRefOid,state:.state}'
gh run list --branch kmp/m7-final --limit 1 --json databaseId,headSha,status,conclusion
./gradlew :androidApp:assembleDebug :shared:jvmTest :shared:testAndroidHostTest --stacktrace  # CI authority; do NOT run local Gradle builds for verdicts
adb -s 127.0.0.1:16416 shell getprop sys.boot_completed
```

## 5. Version-freeze hold

## 6. MSIX trial (M7-D, win-only, UNSIGNED)

- `desktopApp/icons/RePoi.svg` = TRIAL placeholder (replace post-M7).
- 发版 workflow（`release.yml`，tag `v*` / 手动触发）win-only: `:desktopApp:createMsix` → `desktopApp/build/com.example.repoi.msix`
  (asserted non-empty; glob verified empirically on first green run).
- Self-sign/store signing DEFERRED — trial msix is unsigned by design.
- Never touch: `upgradeUuid`, `packageName`, `version`.

AGP 9.4.1 / datastore 1.2.1 / kotlin 2.4.20 / compose 1.12.1 / KSP 2.3.12.
Never touch: `upgradeUuid`, `packageName`, `version`, M1-M6 folders,
`PoiShelf/` + `PodAura-ref/` (read-only refs), `.agents/` (never committed).
