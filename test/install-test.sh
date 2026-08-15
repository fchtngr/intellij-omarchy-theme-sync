#!/bin/bash

set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
TEST_DIR=$(mktemp -d)
trap 'rm -rf -- "$TEST_DIR"' EXIT

bridge_archive=${1:-}
if [[ -z $bridge_archive ]]; then
  mkdir -p "$TEST_DIR/archive/omarchy-theme-sync/lib"
  touch "$TEST_DIR/archive/omarchy-theme-sync/lib/bridge.jar"
  (cd "$TEST_DIR/archive" && zip -qr "$TEST_DIR/bridge.zip" omarchy-theme-sync)
  bridge_archive="$TEST_DIR/bridge.zip"
fi

profile="$TEST_DIR/home/.local/share/JetBrains/IntelliJIdea2026.1"
old_profile="$TEST_DIR/home/.local/share/JetBrains/IntelliJIdea2025.3"
theme="$TEST_DIR/home/.local/state/omarchy/current/theme"
mkdir -p "$profile" "$old_profile" "$theme"
printf '%s\n' \
  'background = "#101010"' \
  'foreground = "#eeeeee"' \
  'accent = "#6699cc"' \
  'selection = "#334455"' \
  'bright_foreground = "#ffffff"' \
  'muted = "#777777"' \
  'red = "#cc6666"' \
  'yellow = "#f0c674"' \
  'green = "#b5bd68"' \
  'cyan = "#8abeb7"' \
  'blue = "#81a2be"' \
  'magenta = "#b294bb"' >"$theme/colors.toml"

HOME="$TEST_DIR/home" \
  XDG_DATA_HOME="$TEST_DIR/home/.local/share" \
  XDG_CACHE_HOME="$TEST_DIR/home/.cache" \
  OMARCHY_INTELLIJ_BRIDGE_ARCHIVE="$bridge_archive" \
  "$ROOT/install.sh"

test -x "$TEST_DIR/home/.config/omarchy/hooks/theme-set.d/omarchy-intellij-theme-sync.py"
compgen -G "$profile/omarchy-theme-sync/lib/*.jar" >/dev/null
test ! -e "$old_profile/omarchy-theme-sync"
test "$(<"$profile/omarchy-theme-sync/.omarchy-managed-version")" = "$(jq -r .version "$ROOT/manifest.json")"
test -s "$TEST_DIR/home/.config/omarchy-intellij/theme.json"
test -s "$TEST_DIR/home/.config/omarchy-intellij/omarchy.xml"
test -s "$TEST_DIR/home/.config/omarchy-intellij/refresh.token"

HOME="$TEST_DIR/home" \
  XDG_DATA_HOME="$TEST_DIR/home/.local/share" \
  XDG_CACHE_HOME="$TEST_DIR/home/.cache" \
  "$ROOT/install.sh" --uninstall
test ! -e "$profile/omarchy-theme-sync"
test ! -e "$TEST_DIR/home/.config/omarchy/hooks/theme-set.d/omarchy-intellij-theme-sync.py"
