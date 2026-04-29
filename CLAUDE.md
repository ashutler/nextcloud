# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

The Nextcloud Android Client synchronizes files from a Nextcloud Server to Android devices. Built with Java, Kotlin, XML, and Jetpack Compose.

## Build Commands

```bash
# Build
./gradlew clean build

# Build specific variant (generic, gplay, versionDev)
./gradlew assembleGenericDebug
./gradlew assembleGplayDebug

# Code quality (run these and fix all findings in modified files)
./gradlew check
./gradlew spotlessApply          # auto-fix formatting
./gradlew spotlessKotlinCheck    # check formatting
./gradlew detekt                 # Kotlin static analysis
./gradlew spotbugsGplayDebug     # bug detection

# Install git hooks (runs spotless/detekt on commit)
./gradlew installGitHooks
```

## Testing

```bash
# Unit tests (no Android SDK required)
./gradlew jacocoTestGplayDebugUnitTest

# Instrumented tests (requires connected device/emulator)
./gradlew createGplayDebugCoverageReport -Pcoverage=true

# Run a specific test class
./gradlew createGplayDebugCoverageReport -Pcoverage=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.owncloud.android.datamodel.FileDataStorageManagerContentProviderClientIT

# Run a single test method
./gradlew createGplayDebugCoverageReport -Pcoverage=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.owncloud.android.datamodel.FileDataStorageManagerIT#methodName

# Screenshot tests (Shot)
SHOT_TEST=true scripts/androidScreenshotTest    # verify
scripts/updateScreenshots.sh                     # regenerate
```

For instrumented tests that require a server: set `NC_TEST_SERVER_USERNAME`, `NC_TEST_SERVER_PASSWORD`, and `NC_TEST_SERVER_BASEURL` in `gradle.properties` (or `~/.gradle/gradle.properties`). Test classes needing a server must extend `AbstractOnServerIT` and use a dedicated test user.

## Code Architecture

### Package Structure

- `com.owncloud.android/` — Legacy components: Activities, data model, file operations, sync adapter, services
- `com.nextcloud/` — Modern components: client APIs, DI, repositories, Compose UI
- `com.nextcloud.client.di/` — Dagger 2 dependency injection configuration
- `com.nextcloud.ui/` — Jetpack Compose screens and reusable components
- `com.nextcloud.utils.extensions/` — Extension functions organized by type (e.g., `FileExtensions.kt`, `ViewExtensions.kt`)
- `com.nextcloud.client.assistant/` — AI Assistant screens (Compose-based)

### Dependency Injection

Dagger 2 is used for automatic injection into `Activity`, `Fragment`, `Service`, `BroadcastReceiver`, and `ContentProvider`. All other components use manual constructor injection. Avoid calling constructors inside constructors.

### Modern UI (Jetpack Compose)

New UI is written in Jetpack Compose with Material Design 3. Use `StateFlow`/`MutableStateFlow` in ViewModels and `collectAsState()` in composables. Use `viewThemeUtils.colorTheme` to respect the server's primary color. Ensure both light and dark themes work.

### Build Variants

- `generic` — no Google dependencies, distributed via F-Droid
- `gplay` — includes Firebase push notifications, distributed via Google Play
- `versionDev` — dev/master snapshot builds

## Code Style

- Line length: **120 characters**; Standard Android Studio formatter with EditorConfig
- Kotlin preferred for new code; legacy Java files remain
- Max **300 lines per file**; each type in its own source file
- No magic numbers; use resources for strings, colors, and dimensions
- Use enums/sealed classes instead of multiple boolean flags
- Apply fail-fast over nested if-else
- No decorative divider comments (e.g., `// ───────`)
- Every new file ends with exactly one blank trailing line

## Every New File

Add an SPDX license header at the top. Replace `<year>` with the current year:

Kotlin/Java:
```kotlin
/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: <year> Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
```

XML:
```xml
<!--
  ~ Nextcloud - Android Client
  ~
  ~ SPDX-FileCopyrightText: <year> Nextcloud GmbH and Nextcloud contributors
  ~ SPDX-License-Identifier: AGPL-3.0-or-later
-->
```

Only edit `app/src/main/res/values/strings.xml` for translations — never modify `values-*` locale directories.

## Commits

- Sign off every commit: `git commit -s`
- Follow Conventional Commits: `feat(files): add folder color picker`, `fix(upload): handle empty filename`
- Include an `AI-assistant` trailer for AI-assisted commits:
  ```
  AI-assistant: Claude Code 2.1.80 (Claude Sonnet 4.6)
  ```
- All PRs target `master`. Use `/backport to stable-X.Y` in PR comments to request backports.
