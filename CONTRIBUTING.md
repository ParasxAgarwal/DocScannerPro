# Contributing

Thank you for contributing to Doc Scanner Pro.

## Development principles

Changes should preserve the application's local-first architecture, native Android behavior and document safety guarantees.

Prefer small, cohesive changes. Keep presentation, document processing, persistence and export responsibilities separated. Avoid introducing network dependencies for functionality that can run locally.

## Before opening a pull request

Run:

```bash
./gradlew test
```

```bash
./gradlew lint
```

Review the release build when a change affects packaging, permissions, native libraries or document processing.

## Code quality

Use idiomatic Kotlin and existing project patterns. Do not add dead screens, unused dependencies or placeholder functionality. New dependencies require license review and a corresponding entry in `docs/THIRD_PARTY_LICENSES.md`.

Document handling must avoid data loss during camera interruption, process recreation and backgrounding.

## Pull requests

Describe the user-facing outcome, implementation scope and test coverage. Keep unrelated refactoring out of feature changes.
