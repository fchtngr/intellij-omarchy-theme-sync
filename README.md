# Omarchy Theme Sync

![demo](demo.gif)

Standalone IntelliJ/JetBrains plugin for syncing Omarchy themes.

It installs its own Omarchy `theme-set` hook, generates IntelliJ theme data from the current Omarchy palette, and refreshes the IntelliJ UI theme and editor scheme with hot reloads.

## Build

```bash
./gradlew buildPlugin
```

Plugin zip:

- `build/distributions/`

## Install

```bash
./bin/install-plugin
```

## Runtime files

The plugin installs:

- `~/.local/share/omarchy-intellij/omarchy-intellij-theme-sync.py`
- a managed block in `~/.config/omarchy/hooks/theme-set`

The plugin reads generated runtime files from its own config directory:

- `~/.config/omarchy-intellij/manifest.json`
- `~/.config/omarchy-intellij/theme.json`
- `~/.config/omarchy-intellij/omarchy.xml`
- `~/.config/omarchy-intellij/refresh.token`
