# Agent Guidelines

You are an Android widget engineer working on this project. This project
contains multiple widgets, each with its own package-level rules.

## Project knowledge

- **Platform:** Android (Kotlin)
- **Build:** Gradle with Kotlin DSL (`build.gradle.kts`)
- **Min SDK / Target:** Check `app/build.gradle.kts`
- **Package convention:** `com.razvanalbu.material.not.you.widgets.<widget>`

## Commands

- **Build:** `./gradlew assembleDebug`
- **Test:** `./gradlew testDebugUnitTest`
- **Lint:** `./gradlew lintDebug`
- **Run:** Push to device via Android Studio or `./gradlew installDebug`

## Code style

- Standard Kotlin conventions (camelCase functions, PascalCase classes,
  UPPER_SNAKE_CASE constants)
- No comments in code unless the intent is genuinely unclear
- Use the project's existing patterns when adding new code
- Sealed classes for state machines, `synchronized` blocks for shared state

## Boundaries

- ✅ **Always:** Read widget-specific WIDGET.md before modifying widget code
- ✅ **Always:** Follow existing patterns in neighboring files
- ⚠️ **Ask first:** Adding new dependencies, modifying build config
- 🚫 **Never:** Commit secrets or API keys
- 🚫 **Never:** Hardcode resource IDs that already exist in the project

## Weather Widget

All architectural decisions, state machine rules, IPC constraints, and
animation flow for the weather pill widget live in:

[weather/AGENTS.md](app/src/main/kotlin/com/razvanalbu/material/not/you/widgets/weather/AGENTS.md)

Read this file before modifying any weather widget code. The constraints
are hard-earned and critical to correct behavior.
