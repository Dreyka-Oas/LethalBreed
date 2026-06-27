---
name: fix-gradle-runclient-remap-error
description: Use when `gradlew runClient` (or runServer) fails at launch with "Failed to remap mods! java.nio.file.ClosedFileSystemException" in the LethalBreed Fabric mod. Gives the reliable relaunch sequence.
---

# Fix: gradle runClient "Failed to remap mods! ClosedFileSystemException"

## Symptom

`gradlew runClient` exits with code 1 shortly after printing the mod list:

```
[main/ERROR] (FabricLoader) Failed to remap mods!
net.fabricmc.loader.impl.FormattedException: java.nio.file.ClosedFileSystemException
    at net.fabricmc.loader.impl.discovery.RuntimeModRemapper.remap(RuntimeModRemapper.java:221)
Caused by: java.nio.file.ClosedFileSystemException
    at jdk.zipfs/jdk.nio.zipfs.ZipFileSystem.ensureOpen(...)
> Task :runClient FAILED
```

## Cause

Fabric Loader's dev-launch step (`RuntimeModRemapper`) remaps the ~116 dependency mods at game start. Their zip filesystems go stale / get closed when **another gradle task ran in the same session right before** `runClient`. This is a flaky Fabric/Loom dev-environment bug — **not** a bug in LethalBreed. A built jar (`gradlew build` → `build/libs/`) does no runtime remap, so the shipped mod never hits this.

## Fix — escalate in this order

It is intermittent. Try cheap first, escalate if it keeps failing.

1. **Don't run a gradle task right before `runClient`.** `runClient` recompiles the mod's dev classes itself, so a separate `build`/`compileJava` is unnecessary and is the main trigger.
2. **Fresh daemon:** `gradlew --stop` then `gradlew runClient`, nothing in between.
3. **Clear the runtime remap cache** (the strong fix — does a clean re-remap, slower first launch). The processed-mods cache holds the stale zip filesystems:

   ```powershell
   Remove-Item -Recurse -Force "F:\projects\LethalBreed\run\.fabric\processedMods","F:\projects\LethalBreed\run\.fabric\tmp" -ErrorAction SilentlyContinue
   gradlew --stop
   gradlew runClient
   ```

4. If it *still* fails, check for an orphaned game JVM holding the jars and kill it, then retry step 3:

   ```powershell
   Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Select ProcessId, CommandLine
   ```

Steps 1–2 are flaky (observed `--stop` + `runClient` fail on its own). Step 3 (clear `processedMods`) is usually reliable — but observed it **still recur** across several back-to-back relaunches. When it does, escalate to the nuclear fix:

5. **Wipe the whole `run\.fabric` dir + kill every stray game/daemon JVM, then a single `runClient` with no preceding gradle task:**

   ```powershell
   cd F:\projects\LethalBreed
   Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
   Start-Sleep 2
   Remove-Item -Recurse -Force "run\.fabric" -ErrorAction SilentlyContinue
   .\gradlew runClient   # nothing (no build/--stop) between this and the wipe
   ```

   Observed: clearing only `processedMods`+`tmp` failed 3× in a row; wiping all of `run\.fabric` after killing JVMs booted clean on the next try. A leftover game JVM from a previous launch holding the dep-mod zips is the usual culprit.

## If you must verify compilation first

Run `gradlew compileJava` to check for errors, but then `--stop` is **not** always enough (observed it still fail once after `compileJava` + `--stop` + `runClient`). Prefer: verify compile, accept the result, then launch via `--stop` + `runClient` and retry on failure. Treat a remap failure as transient, never as a code problem — read the stack trace to confirm it is `ClosedFileSystemException` and not an actual mod exception before retrying.

## Notes

- This box: Bash tool is broken (fork errors) — run gradle via PowerShell. See the `use-powershell-not-bash` skill.
- Run `runClient` in the background (it is a long-lived GUI) and tail the task output log for `[LethalBreed][PERF]` lines and any `com.dreykaoas` stack traces.
