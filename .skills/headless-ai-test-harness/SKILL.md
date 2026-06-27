---
name: headless-ai-test-harness
description: Use to test LethalBreed zombie AI (approach, climb, descend, block ops) autonomously — no Minecraft client, no manual commands, no MCP. Build a deterministic arena on server start, force-load it, log per-zombie state, read it from the dedicated-server log.
---

# Headless AI test harness for LethalBreed

Lets you verify mob-AI changes yourself from a dedicated server's log — far more precise for pathfinding
than eyeballing a screenshot.

## Pieces (already in the repo)

- `command/ClimbTest.java` — on `ServerLifecycleEvents.SERVER_STARTED`, when `LethalBreedConfig.devClimbTest`
  is on, it: force-loads the arena chunks (`level.setChunkForced(cx>>4, cz>>4, true)`) so entities tick
  with **no player online**; turns `forceDayTime` off + holds night (zombies don't burn); builds a flat
  stone platform (slope-free geometry); puts a wall + a `setNoAi`/`setNoGravity`/`setInvulnerable` villager
  on top as a stationary target; spawns a few zombies a short walk away; sets `debugClimb = true`.
- `entity/SmartZombie` debug lines (gated by `LethalBreedConfig.debugClimb`) log each targeting zombie's
  `y, tgtY, horiz, dy, stuck, climb, cd, ground` every few activations, plus a `CLIMBING ... risen=` line
  from `climbStep`.

## Run it

1. Set `devClimbTest = true` in `LethalBreedConfig`.
2. `gradlew compileJava`, then delete `run/world` for a clean arena, clear the remap cache, fresh daemon:
   ```powershell
   Remove-Item -Recurse -Force run/world, run/.fabric/processedMods, run/.fabric/tmp -ErrorAction SilentlyContinue
   gradlew --stop
   gradlew runServer            # run_in_background
   ```
3. Poll the task log until `[ClimbTest] flat arena` appears, then read `[ClimbDbg]` lines. Foreground
   `Start-Sleep` is blocked here, but `[System.Threading.Thread]::Sleep(2000)` inside a poll loop works.
4. Read behaviour: zombies reaching `y=tgtY, dy=0` next to the villager = climb works; `risen` climbing
   then resetting to 0 = falling/looping (usually crowding on a 1-wide wall).
5. To stop the server: `Get-Process java,javaw | Stop-Process -Force`.
6. **When done: set `devClimbTest = false`** (and rebuild) so the arena never builds in the real game.

Notes: locale here prints decimals with commas (`-58,2` = -58.2). Bash is broken — use PowerShell. See
also `fix-gradle-runclient-remap-error`.
