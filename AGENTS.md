# Repository Guidelines

## Project Structure & Module Organization

TSF is a Fabric mod for Minecraft 26.3, targeting Java 25. Dependency and mod versions are defined in `gradle.properties`.

- `src/main/java/icu/tatyrealms/tatykefei/`: shared and server code, including `item/`, `state/`, `network/`, `compat/`, and `mixin/`.
- `src/client/java/icu/tatyrealms/tatykefei/client/`: screens, rendering, observer synchronization, and client mixins.
- `src/main/resources/assets/tsf/`: built-in textures, item models, translations, and mod icon.
- `src/main/resources/data/`: recipes, animation definitions, and Trinkets slot definitions and tags.
- `.github/workflows/build.yml`: CI build and JAR artifact upload. `build/` and `run/` are generated directories.

## Build, Test, and Development Commands

Use JDK 25 and the checked-in Gradle wrapper:

- `./gradlew build`: compile both source sets, run configured checks, and package JARs in `build/libs/`.
- `./gradlew test`: run tests when present; currently reports `NO-SOURCE`.
- `./gradlew runClient`: launch the development client.
- `./gradlew runServer`: launch the development server; accept its EULA before use.
- `./gradlew --offline build`: build using cached dependencies after initial setup.

Distribute the regular JAR, not the `-sources.jar` artifact.

## Coding Style & Naming Conventions

Use four-space Java indentation, same-line opening braces, and UTF-8. Keep packages under `icu.tatyrealms.tatykefei`. Use PascalCase types, camelCase members, UPPER_SNAKE_CASE constants, and `tsf$` prefixes for mixin additions. Resource identifiers use the `tsf` namespace and snake_case paths. Update both `zh_cn.json` and `en_us.json` for new user-facing text. Follow surrounding JSON formatting; no formatter or linter is configured.

## Testing Guidelines

There is no automated test suite, configured testing framework, or coverage threshold. Run `./gradlew build` and manually verify affected behavior. Use two clients for possession, observer inventory, chat, and disconnect changes. Check rendering in relevant display contexts. Verify optional integrations with and without Trinkets Updated and NeedsOfNature. Report checks performed and any untested behavior.

## Compatibility & State Management

Keep client classes out of shared code. Optional integrations must tolerate absent mods. Mutate Minecraft state on its owning game thread; background persistence should receive detached snapshots. Server settings live in `config/tsf-server.json`.

## Commit & Pull Request Guidelines

History favors concise Chinese subjects describing a concrete change, such as `修复展示框中肉体和皮物被压扁及悬空的问题`. Commit each completed task separately; preserve unrelated edits and do not push without explicit instruction. Keep the mod version unchanged unless requested. PRs should explain resulting behavior, reference relevant issues, list validation, and include screenshots for visual changes.
