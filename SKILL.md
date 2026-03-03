<!--
 * Project: Lethal Breed
 * Responsibility: Master Skill (O.A.S Standards)
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
-->
# O.A.S Master Skill - Lethal Breed

## 🎯 Global Mandates (Zero-Bloat)
- **STRICT 50-line limit** per file. Modularize everything.
- **English-Only:** All code, comments (the *why*), and commits.
- **MS-RSL License:** MUST be present in every file header.
- **Bleeding Edge:** Java 25.0.2-graalce | Fabric 1.21.11.

## 🛠️ Minecraft Modding Core
- **Server-Side Priority:** All AI/Logic must be server-side.
- **Performance:** Multithreaded world scans (LethalThreads).
- **AI Architecture:** FSM (Finite State Machine) in `BuildStateMachine`.
- **Hearing System:** `HearingRegistry` for sound-based tracking.
- **Config:** Hierarchical JSON in `config/o.a.s/lethalbreed.json`.

## ⚙️ Coding Standards (Quality)
- **Naming:** Explicit, descriptive, no single-letter variables.
- **Robustness:** No empty catch blocks. Use Result/Option patterns.
- **Localization:** No hardcoded strings. Use `.json` lang files.
- **Vanilla-First:** Native over libraries (e.g., Vanilla CSS for UI).

## 🧪 Workflow & Validation
- **TDD:** Unit/integration tests for every feature.
- **Validation:** Changes only complete after behavioral confirmation.
- **Branding:** "Brutalist-Tech" UI (High contrast, thick borders).
