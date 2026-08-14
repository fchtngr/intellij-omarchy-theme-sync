# Omarchy Theme Sync

![demo](demo.gif)

JetBrains IDE plugin for syncing [Omarchy](https://github.com/basecamp/omarchy) themes.

This plugin targets Omarchy Quattro (Omarchy 4) and its semantic theme palette.

It installs its own Omarchy `theme-set` hook, generates IntelliJ theme data from the current Omarchy palette, and refreshes the IntelliJ UI theme and editor scheme with hot reloads.

## Build

Requires Java 17 and the Gradle wrapper (managed via `mise.toml`):

```bash
./gradlew buildPlugin
```

Plugin zip:

- `build/distributions/`

## Verify

Run the IntelliJ Plugin Verifier against the resolved target IDE (2024.1) and the latest release:

```bash
./gradlew verifyPlugin
```

The verifier must pass on `COMPATIBILITY_PROBLEMS` and `OVERRIDE_ONLY_API_USAGES`. The plugin deliberately uses the internal `UITheme.loadTempThemeFromJson` API, so `INTERNAL_API_USAGES` is excluded from the failure level, and the `TemplateWordInPluginId` check is muted for the published plugin id (`at.fchtngr.omarchy.intellij`).

## CI

- `.github/workflows/verify.yml` runs `./gradlew verifyPlugin` on every push to `master` and pull request.
- `.github/workflows/release.yml` builds and attaches the plugin ZIP to a GitHub release when a `v*` tag is pushed.

## Release

Push a version tag to build the plugin ZIP and attach it to a GitHub release:

```bash
git tag v0.0.6
git push origin v0.0.6
```

The release workflow derives the plugin version from the tag, so `v0.0.6` builds the plugin with version `0.0.6`. The `pluginVersion` in `gradle.properties` is only a local development fallback.

## Install

For normal use, install the plugin from JetBrains Marketplace once it is published.

For local testing, build the plugin and install the generated ZIP from disk via the IDE:

```bash
./gradlew buildPlugin
```

Then open `Settings | Plugins | ⚙ | Install Plugin from Disk...` and select the ZIP from `build/distributions/`.

## Runtime files

The plugin installs:

- `~/.local/share/omarchy-intellij/omarchy-intellij-theme-sync.py`
- `~/.config/omarchy/hooks/theme-set.d/omarchy-intellij-theme-sync`

The plugin reads generated runtime files from its own config directory:

- `~/.config/omarchy-intellij/manifest.json`
- `~/.config/omarchy-intellij/theme.json`
- `~/.config/omarchy-intellij/omarchy.xml`
- `~/.config/omarchy-intellij/refresh.token`

The generator reads Omarchy Quattro's active theme directory
(`~/.local/state/omarchy/current/theme`). It is invoked by the
`~/.config/omarchy/hooks/theme-set.d/` hook.
