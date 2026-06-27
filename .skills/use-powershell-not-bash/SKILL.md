---
name: use-powershell-not-bash
description: Use when a Bash tool command in this environment fails with "dofork: child ... died unexpectedly" / "fork: Resource temporarily unavailable" (exit 53/254). Run shell work through PowerShell instead.
---

# Fix: Bash tool fork errors — use PowerShell

## Symptom

Any Bash tool command dies before running, e.g.:

```
0 [main] bash ... dofork: child -1 - forked process died unexpectedly, retry 0, exit code 0xC0000142, errno 11
/etc/profile: fork: retry: Resource temporarily unavailable
```

Plain `git`/`ls` via Bash exit 53 or 254.

## Cause

Git Bash fork emulation is broken on this Windows box (cygwin `fork()` fails). Environment-wide, not project-specific.

## Fix

Run all shell work via the **PowerShell** tool. Use dedicated tools where possible (Read / Glob / Grep / Edit) instead of shelling out.

Common swaps:
- `git -C "F:\projects\LethalBreed" log --oneline -15` (set `$env:GIT_PAGER='cat'` to avoid a pager)
- `gradlew` builds/launches: PowerShell `& "F:\projects\LethalBreed\gradlew.bat" -p "F:\projects\LethalBreed" <task>`
- Long-lived launches (`runClient`): run with `run_in_background: true` and tail the task output file.
- Note: PowerShell here blocks chained `Start-Sleep`; to wait, use a background task and react to its completion notification rather than sleeping.
