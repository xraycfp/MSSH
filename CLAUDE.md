# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MSSH is a mobile SSH client built with Kotlin Multiplatform (KMP) and Compose Multiplatform. Currently targets Android only. The README is in Chinese (简体中文).

## Build Commands

```bash
# Build debug APK
./gradlew :composeApp:assembleDebug

# Build release APK (ProGuard enabled)
./gradlew :composeApp:assembleRelease

# Generate SQLDelight sources
./gradlew :composeApp:generateCommonMainMsshDatabaseInterface
```

No test infrastructure is currently configured.

## Architecture

**Pattern**: MVVM + Repository, with Koin dependency injection.

### Source Set Layout (KMP)

- `composeApp/src/commonMain/` — Platform-independent code: data models, repository interfaces, terminal emulator, UI screens/ViewModels for hosts, navigation routes, theme
- `composeApp/src/androidMain/` — Android-specific code: repository implementations, SSH layer (ConnectBot sshlib wrapper), database driver, terminal UI (Canvas rendering), key management, foreground service

### Key Subsystems

**SSH Connection** (`androidMain/ssh/`): `SshConnectionManager` wraps ConnectBot's Trilead SSH library. Connection lifecycle follows a sealed-class state machine: Idle → Connecting → Authenticating → Connected → Disconnected/Error. Supports password and public key auth.

**Terminal Emulation** (`commonMain/terminal/`): Custom VT100/xterm emulator. `TerminalEmulator` parses ANSI escape sequences (cursor movement, colors including 256-color and 24-bit RGB, scrolling regions). `TerminalBuffer` manages a 2D cell grid with dirty-region tracking. Rendered via Android Canvas in `TerminalCanvas.kt`.

**Data Layer**: SQLDelight schemas in `composeApp/src/commonMain/sqldelight/com/mssh/data/db/` (Host.sq, SshKey.sq, KnownHost.sq). Repository interfaces in commonMain, implementations in androidMain. All repositories expose coroutine Flows.

**DI Setup**: `CommonModule.kt` provides shared ViewModels. `AndroidModule.kt` provides database, repositories, SSH manager, and Android-specific ViewModels. Koin is initialized in `MainApplication.onCreate()`.

**Navigation**: Type-safe routes via `@Serializable` data classes in `Route.kt`. NavHost setup in `App.kt`.

### Data Flow

```
Compose UI → ViewModel (StateFlow) → Repository (Flow) → SQLDelight Database
                ↕
         SshConnectionManager → ConnectBot sshlib → Remote SSH Server
                ↕
         TerminalEmulator / TerminalBuffer
```

## Dependency Management

All versions centralized in `gradle/libs.versions.toml`. Key dependencies:
- **Kotlin** 2.1.20, **Compose Multiplatform** 1.7.3
- **ConnectBot sshlib** 2.2.43 (SSH protocol)
- **Koin** 4.0.3 (DI), **SQLDelight** 2.0.2 (database)
- **Android**: minSdk 26, targetSdk/compileSdk 34, JVM target 17

## Conventions

- Kotlin coroutines throughout: `Dispatchers.IO` for SSH/database operations, structured concurrency via `viewModelScope`
- ViewModels use `StateFlow` for UI state; Compose observes via `collectAsState()`
- SSH key types: RSA-4096, Ed25519, ECDSA-256, ECDSA-384
- App supports SSH URI scheme intent for launching connections from external apps
