<!--
 * Project: Lethal Breed
 * Responsibility: Master Update Plan (O.A.S Compliance)
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
-->
# 🗺️ Lethal Breed - O.A.S Compliance Plan

This plan details the steps required to make the project 100% compliant with the **[O.A.S Master Skill](./SKILL.md)**.

## 🏗️ Phase 1: Infrastructure & Standards
- [x] Update `build.gradle` to **Java 25**.
- [x] Inject **MS-RSL** header in all `.java` files.
- [x] Inject **MS-RSL** header in all `.tsx` and `.ts` files.
- [x] Inject **MS-RSL** header in all `.md` files.
- [x] Verify `fabric.mod.json` contains O.A.S license mention.
- [x] **Full Translation:** Convert all remaining comments, logs, and docs to **English**.
- [x] **Localization:** Externalize hardcoded strings in `lang/en_us.json` and `lang/fr_fr.json`.

## ⚙️ Phase 2: Modularization & Performance
- [x] Create `oas.work.lethalbreed.config.model` package.
- [x] Modularize `LethalThreads.java` & `HearingRegistry.java` (< 50 lines).
- [x] Isolate `ConfigValidator.java` (reflection logic).
- [x] Reduce `ModConfig.java` to < 50 lines.

## 🧠 Phase 3: AI Restructuring (FSM & Goals)
- [x] **BuildStateMachine.java**: Extract state logic (Chase, Jump, Break).
- [x] Modularize `ClosestVisibleTargetGoal`, `KamikazeGoal`, `MovementCoordinator`.
- [x] **ObstructionAnalyzer.java**: Extract sector scans.
- [x] **Server Audit:** Verify critical logic is **Server-Side Only**.

## 🎨 Phase 4: UI, Branding & Overlay
- [x] Implement **Overlay** progress messages (`sendMessage(..., true)`).
- [x] YACL Integration (Descriptions, `.step() > 0`).
- [x] **Branding & Style:** "Brutalist-Tech" & "Vanilla-First" CSS audit.

## 🧪 Phase 5: Testing & Quality (TDD)
- [ ] Implement unit tests for `HearingRegistry` and `BuildStateMachine`.
- [ ] Validate test coverage for configuration logic.

## 🌐 Phase 6: Website (Frontend)
- [ ] Audit: React components < 50 lines & MS-RSL headers.

## ✅ Phase 7: Final Validation
- [ ] Code Audit: Zero single-letter variables & zero empty catches.
- [ ] Compilation (`./gradlew build`) and Client/Server tests.
- [ ] Final check of the 50-line limit across the entire project.

---
*Last Update: February 22, 2026*
