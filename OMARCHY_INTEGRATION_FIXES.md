# Omarchy integration fixes

Address these issues before relying on the plugin hook in normal Omarchy installs.

## 1. Install a hook file under `theme-set.d/`

Do not modify `~/.config/omarchy/hooks/theme-set` directly. Current Omarchy supports hook directories and runs both:

- `~/.config/omarchy/hooks/theme-set`
- `~/.config/omarchy/hooks/theme-set.d/*`

The plugin should install its own executable hook file at:

```text
~/.config/omarchy/hooks/theme-set.d/omarchy-intellij-theme-sync
```

This avoids overwriting or changing a user's existing `theme-set` hook.

Suggested `OmarchyHookInstaller.kt` shape:

```kotlin
private val hookPath: Path = Paths.get(
    System.getProperty("user.home"),
    ".config", "omarchy", "hooks", "theme-set.d", "omarchy-intellij-theme-sync"
)

private fun installHook() {
    Files.createDirectories(hookPath.parent)
    val body = "#!/bin/bash\n\"$syncScript\" \"\$@\"\n"
    Files.writeString(hookPath, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    hookPath.toFile().setExecutable(true, false)
}
```

## 2. Do not inject shell options into user hooks

Remove the current managed-block editing of `~/.config/omarchy/hooks/theme-set`.

The current implementation prepends:

```bash
#!/usr/bin/env bash
set -euo pipefail
```

to an existing user hook when no plugin block is present. This is risky because:

- Omarchy style uses `#!/bin/bash`.
- `omarchy-hook` already runs hook files via `bash "$HOOK_PATH"`.
- Adding `set -euo pipefail` can change or break user hook behavior.
- The plugin should not rewrite user-owned hook scripts.

## 3. Use the theme name passed by Omarchy

`omarchy-theme-set` invokes hooks as:

```bash
omarchy-hook theme-set "$THEME_NAME"
```

The hook already passes `"$@"` to the Python script, but the Python script currently ignores it and derives the name from:

```python
DEFAULT_THEME_DIR.name
```

Since the path is always `~/.config/omarchy/current/theme`, this produces names like `Omarchy Theme` instead of the real theme name.

Update `omarchy-intellij-theme-sync.py` to prefer `sys.argv[1]`, then fall back to `~/.config/omarchy/current/theme.name`.

Suggested code:

```python
def display_theme_name() -> str:
    theme_name_file = Path.home() / '.config/omarchy/current/theme.name'
    if len(sys.argv) > 1 and sys.argv[1].strip():
        raw = sys.argv[1].strip()
    elif theme_name_file.exists():
        raw = theme_name_file.read_text(encoding='utf-8').strip()
    else:
        raw = DEFAULT_NAME

    raw = raw.replace('-', ' ').replace('_', ' ').title()
    return f'Omarchy {sanitize_name(raw)}'
```

Then in `main()` use:

```python
name = display_theme_name()
```

## 4. Keep reading colors from the current theme path

This part is correct and should stay:

```python
DEFAULT_THEME_DIR = Path.home() / '.config/omarchy/current/theme'
colors_file = DEFAULT_THEME_DIR / 'colors.toml'
```

Omarchy stages the active theme at this path before running the `theme-set` hook.

## 5. Re-test build/toolchain

A local build currently fails very early with:

```text
FAILURE: Build failed with an exception.

* What went wrong:
25.0.1
```

After applying the integration changes, re-run:

```bash
./gradlew test buildPlugin --stacktrace
```

and fix the Gradle/JDK/IntelliJ plugin toolchain issue separately if it remains.
