# E2E_MATRIX.md — M7 on-device matrix (e2e_spec §6; device `127.0.0.1:16416`)

Baseline: MuMu vmindex 1, Android 15, portrait 1440x2560 (landscape via `wm`).
Selectors: Chinese-label-first; `testTags` on textless nodes only.
Device is parent-managed (mumu-cli); worker uses `adb -s` only.

## Slice 1 (smoke slice): F1 / F4 / F39

| ID | Flow | Pass criteria |
|---|---|---|
| F1 | cold launch → home feed first page | items render, no FATAL |
| F4 | feed → detail open | detail header + pics load |
| F39 | settings toggle (ExHobby) persists | relaunch keeps value |

## Full matrix (D — manual acceptance)

- Auth: login success / failure message / logout.
- Home feed: initial / refresh / next-page / category + subtype filters / reset.
- Album: tabs / order switch / ranking sections / praise top (day/week/month).
- Detail: info / timeline / albums / related / comments paging / collect toggle.
- Message: center / chat send+withdraw / notice batch ops / block unblock.
- Resale/search/forum/zone/vendor/person/works/character/person flows per M4-M5.
- Settings + theme + search-history + view-prefs persistence across relaunch.

## Per-case evidence

- `screencap` portrait + landscape for key screens.
- `logcat -d | Select-String "FATAL EXCEPTION"` clean per case.
- Orientation: `adb -s 127.0.0.1:16416 shell wm size` record before/after.

## Drop rule

Device drop mid-E2E → STOP-report immediately; no faked screencap/logcat.
