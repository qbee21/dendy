# Dendy 2026 Architecture

## Goals

- Move from a single `app` shell to a multi-module Android project.
- Standardize on `Kotlin + Jetpack Compose + Material 3`.
- Keep the app architecture `Clean Architecture + MVVM`.
- Target `minSdk 28` and Android 9+.
- Treat libretro as the first real emulation integration point while keeping the engine replaceable.
- Keep built-in ROM support legal-safe from day one.

## Current Scaffold In This Repo

This repository now contains the first architecture scaffold:

```text
app
core-model
core-data
core-emulation
feature-library
feature-player
feature-settings
docs
```

The current shell intentionally still uses temporary stand-ins for user ROMs, save-state persistence and native emulation, but built-in ROM packaging now has a real first-pass implementation.

## Current Built-In Pack

The repository now ships a small verified built-in pack backed by:

- `app/src/main/assets/built_in_catalog.json`
- `app/src/main/assets/roms/`
- `app/src/main/assets/licenses/`

Current included ROM:

- `Big City Sliding Blaster`
- `Double Action Blaster Guys`
- `robotfindskitten`

See `docs/BUILT_IN_CATALOG.md` for the auditable list and packaging rules.

## Remaining Temporary Stand-Ins

These are still temporary and will be replaced in later iterations:

- `InMemory*Repository`
- `FakeEmulationSessionFactory`
- `FakeLibretroBridge`

These keep the project bootable while we replace each seam with the real implementation.

## Module Responsibilities

### `app`

- Application entry point and DI composition root.
- Compose navigation host and theme.
- App startup bootstrap for built-in ROM installation and scanning.

### `core-model`

- Shared domain models and value objects.
- ROM identity, location and metadata.
- Save-slot summaries.
- Touch layout and performance settings.

### `core-data`

- Built-in installer boundary.
- Scanner boundary and ROM catalog contracts.
- Repositories for built-in ROM, imported ROM, recents, save states and settings.
- Future home for DataStore persistence and indexed storage.

### `core-emulation`

- `EmulationSessionFactory` and `EmulationSession`.
- Replaceable `CoreProvider`.
- `LibretroBridge` seam for JNI/native integration.
- `RendererHost`, `InputSink` and `InputMapper`.

### `feature-library`

- Main library route.
- Hero block for continue/resume.
- Built-in vs My ROM segmented browsing.
- Search/filter shell and import CTA.

### `feature-player`

- Fullscreen player route.
- Compose overlay over a non-Compose renderer host.
- Quick save/load and pause interactions.
- Placeholder touch controls aligned with future layout editor work.

### `feature-settings`

- Video, audio and performance settings UI.
- Future home for touch layout editor settings and per-core options.

## Public Types And Contracts

### Repositories

- `BuiltInCatalogRepository`
- `UserRomRepository`
- `RecentGamesRepository`
- `SaveStateRepository`
- `SettingsRepository`
- `RomCatalogRepository`

### Emulation

- `EmulationSessionFactory`
- `EmulationSession`
- `CoreProvider`
- `LibretroBridge`
- `RendererHost`
- `InputSink`
- `InputMapper`

### Shared Models

- `RomId`
- `RomSource`
- `RomLocation`
- `RomEntry`
- `GameMetadata`
- `SaveSlot`
- `SaveStateSummary`
- `TouchControlLayout`
- `PerformancePreset`
- `BuiltInInstallState`
- `DendySettings`

## Built-In ROM Policy

- The built-in catalog must contain only legal ROMs: homebrew, public-domain or properly licensed content.
- The directory shape is already reserved under `app/src/main/assets/roms/` and `app/src/main/assets/covers/`.
- Installation targets `Context.getFilesDir()` by default.
- `getExternalFilesDir()` remains reserved for user-visible import and export flows.

## Near-Term Implementation Order

1. Replace stub startup with a real `BuiltInRomInstaller` that copies assets idempotently and survives partial installs.
2. Implement real `RomScanner` support for `.nes` and `.zip`, hash normalization and dedupe.
3. Replace in-memory repositories with indexed storage plus DataStore-backed settings.
4. Wire a JNI-based `LibretroBridge` and move the fake renderer host behind the real surface pipeline.
5. Add SAF import, save-state previews, touch layout editor and core options.

## Testing Targets

### Unit

- Installer is idempotent and resilient to partial copy state.
- Scanner distinguishes built-in and imported sources and deduplicates by hash.
- Save-state index stores quick slots, named slots and resume state correctly.
- Settings restore filter, touch layout and performance preset.

### Integration

- First launch installs built-in assets and populates the library.
- SAF import adds ROMs into `Мои ROM`.
- Quick save/load works without recreating the session.
- Rotation keeps overlay and touch layout stable.
- Background/foreground fully releases native libretro resources.

### UI

- Adaptive library grid on compact and expanded widths.
- Player overlay and pause menu.
- Dynamic color fallback on devices before Monet.
