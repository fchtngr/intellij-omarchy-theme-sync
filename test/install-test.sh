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

plugin_root="$TEST_DIR/plugin"
mkdir -p "$plugin_root/scripts"
cp "$ROOT/install.sh" "$ROOT/manifest.json" "$plugin_root/"
cp "$ROOT/scripts/omarchy-intellij-theme-sync.py" "$plugin_root/scripts/"
checksum=$(sha256sum "$bridge_archive")
checksum=${checksum%% *}
jq --arg checksum "$checksum" '.bridgeSha256 = $checksum' "$plugin_root/manifest.json" >"$plugin_root/manifest.json.tmp"
mv "$plugin_root/manifest.json.tmp" "$plugin_root/manifest.json"

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

version=$(jq -r .version "$plugin_root/manifest.json")
cache_archive="$TEST_DIR/home/.cache/omarchy-intellij-theme-sync/omarchy-theme-sync-$version.zip"
mkdir -p "${cache_archive%/*}"
cp "$bridge_archive" "$cache_archive"

HOME="$TEST_DIR/home" \
  XDG_DATA_HOME="$TEST_DIR/home/.local/share" \
  XDG_CACHE_HOME="$TEST_DIR/home/.cache" \
  "$plugin_root/install.sh"

test -x "$TEST_DIR/home/.config/omarchy/hooks/theme-set.d/omarchy-intellij-theme-sync.py"
compgen -G "$profile/omarchy-theme-sync/lib/*.jar" >/dev/null
test ! -e "$old_profile/omarchy-theme-sync"
test "$(<"$profile/omarchy-theme-sync/.omarchy-managed-version")" = "$version"
test -s "$TEST_DIR/home/.config/omarchy-intellij/theme.json"
test -s "$TEST_DIR/home/.config/omarchy-intellij/omarchy.xml"
test -s "$TEST_DIR/home/.config/omarchy-intellij/refresh.token"

HOME="$TEST_DIR/home" \
  XDG_DATA_HOME="$TEST_DIR/home/.local/share" \
  XDG_CACHE_HOME="$TEST_DIR/home/.cache" \
  "$plugin_root/install.sh" --uninstall
test ! -e "$profile/omarchy-theme-sync"
test ! -e "$TEST_DIR/home/.config/omarchy/hooks/theme-set.d/omarchy-intellij-theme-sync.py"

mkdir -p "$TEST_DIR/tampered/omarchy-theme-sync"
printf x >"$TEST_DIR/tampered/omarchy-theme-sync/tampered"
(cd "$TEST_DIR/tampered" && zip -q "$cache_archive" omarchy-theme-sync/tampered)
unzip -tq "$cache_archive" >/dev/null
mkdir -p "$TEST_DIR/bin"
printf '#!/bin/sh\nexit 1\n' >"$TEST_DIR/bin/curl"
chmod +x "$TEST_DIR/bin/curl"
if HOME="$TEST_DIR/home" \
  PATH="$TEST_DIR/bin:$PATH" \
  XDG_DATA_HOME="$TEST_DIR/home/.local/share" \
  XDG_CACHE_HOME="$TEST_DIR/home/.cache" \
  "$plugin_root/install.sh" 2>/dev/null; then
  echo "Installer accepted a bridge archive with the wrong checksum" >&2
  exit 1
fi
test ! -e "$profile/omarchy-theme-sync"
