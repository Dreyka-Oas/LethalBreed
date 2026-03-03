<!--
 * Project: Lethal Breed
 * Responsibility: Project Context and Guidelines
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
-->
# Lethal Breed - Project Context & Guidelines

## 🚀 Project Overview
**Lethal Breed** is a high-intensity Minecraft mod for Fabric (1.21.11) that overhauls zombie AI into intelligent, adaptive predators. It features advanced hearing, building/mining capabilities, and complex state-driven behaviors.

- **Mod Loader:** Fabric 1.21.11 (Loader 0.18.3)
- **Java Version:** 25.0.2-graalce (SDKMAN path: `/home/dreykaoas/.sdkman/candidates/java/25.0.2-graalce`)
- **Website:** React + Vite + Tailwind CSS Wiki (`/website`)
- **Core Goal:** Transform zombies into strategic threats that can mine, build bridges, and track players by sound.

## 🏗️ Technical Architecture
### AI & Mechanics
- **Finite State Machine (FSM):** Managed in `BuildStateMachine.java` with states for chasing, climbing, jumping, and mining.
- **Hearing System:** `HearingRegistry` and `HearingEventMixin` allow entities to react to sounds in the world.
- **Multithreading:** `LethalThreads.java` handles heavy world/obstruction scans to prevent server lag.
- **Config:** Hierarchical JSON at `config/o.a.s/lethalbreed.json`, managed by `ModConfig.java`.

### Project Structure
- `loaders/fabric/1.21.11/`: Main mod source, build files, and resources.
- `website/`: Frontend React application for the mod's wiki/documentation.
- `SKILL.md`: Master Skill mandates (50-line limit, MS-RSL headers).
- `UPDATE_PLAN.md`: Tracking O.A.S compliance and modularization progress.

## ⚙️ Building & Running
### Mod (Fabric)
- **Build:** `./gradlew build` in `loaders/fabric/1.21.11/`
- **Run Client:** `./gradlew runClient` (Requires Java 25)
- **Run Server:** `./gradlew runServer --args="nogui"`
- **Output:** `loaders/fabric/1.21.11/build/libs/lethalbreed-0.1.0.jar`

### Website (React)
- **Install:** `npm install` in `website/`
- **Dev:** `npm run dev`
- **Build:** `npm run build`

## 📜 Development Conventions (O.A.S Standards)
1. **STRICT 50-Line Limit:** No file should exceed 50 lines. Modularize logic into specialized components.
2. **MS-RSL Header:** Every file MUST start with the O.A.S - MS-RSL license header.
3. **English-Only:** All code, comments (explaining "why"), and documentation must be in English.
4. **Vanilla-First:** Prefer platform-native primitives (Vanilla CSS for UI, native Fabric/Minecraft APIs for logic).
5. **Robust Error Handling:** Favor Result/Option patterns; never use empty catch blocks.
6. **Naming:** Use explicit, descriptive names; avoid single-letter variables or generic terms like `data`/`temp`.

## 🧪 Testing & Validation
- **Verification:** Changes are only considered complete after behavioral validation in-game.
- **TDD:** New features should include unit or integration tests where feasible.
- **Branding:** Follow the "Brutalist-Tech" aesthetic (thick borders, high contrast, specific O.A.S green/blue colors).
