#!/bin/bash

set -euo pipefail

PLUGIN_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
VERSION=$(jq -er '.version' "$PLUGIN_DIR/manifest.json")
SYNC_SCRIPT="$PLUGIN_DIR/scripts/omarchy-intellij-theme-sync.py"
HOOK_PATH="$HOME/.config/omarchy/hooks/theme-set.d/omarchy-intellij-theme-sync.py"
DATA_HOME=${XDG_DATA_HOME:-"$HOME/.local/share"}
JETBRAINS_DIR="$DATA_HOME/JetBrains"
PLUGIN_NAME="omarchy-theme-sync"
CACHE_DIR="${XDG_CACHE_HOME:-"$HOME/.cache"}/omarchy-intellij-theme-sync"
CACHE_ARCHIVE="$CACHE_DIR/$PLUGIN_NAME-$VERSION.zip"
RELEASE_ARCHIVE="https://github.com/fchtngr/intellij-omarchy-theme-sync/releases/download/v$VERSION/$PLUGIN_NAME-bridge.zip"

[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$ ]] || {
  echo "Invalid plugin version: $VERSION" >&2
  exit 1
}

notify() {
  command -v omarchy-notification-send >/dev/null && omarchy-notification-send "$1" >/dev/null 2>&1 || true
}

validate_archive() {
  local archive=$1 entries
  entries=$(unzip -Z1 "$archive") || return 1
  ! grep -Eq '(^/|(^|/)\.\.(/|$))' <<<"$entries" &&
    grep -Eq "^$PLUGIN_NAME/lib/[^/]+\.jar$" <<<"$entries"
}

uninstall() {
  local product_dir target

  rm -f -- "$HOOK_PATH"
  if [[ -d $JETBRAINS_DIR ]]; then
    for product_dir in "$JETBRAINS_DIR"/*20*/; do
      [[ -d $product_dir ]] || continue
      target="$product_dir/$PLUGIN_NAME"
      [[ -f $target/.omarchy-managed-version ]] || continue
      rm -rf -- "$target"
    done
  fi
  rm -f -- "$CACHE_ARCHIVE"
  echo "Removed the Omarchy IntelliJ theme hook and managed bridges."
}

if [[ ${1:-} == "--uninstall" ]]; then
  uninstall
  exit
fi

install -Dm755 "$SYNC_SCRIPT" "$HOOK_PATH"

product_dirs=()
if [[ -d $JETBRAINS_DIR ]]; then
  for product_dir in "$JETBRAINS_DIR"/*20*/; do
    [[ -d $product_dir ]] || continue
    [[ $(basename "$product_dir") =~ 20(2[4-9]|[3-9][0-9])\.[0-9]+ ]] || continue
    product_dirs+=("$product_dir")
  done
fi

if (( ${#product_dirs[@]} == 0 )); then
  echo "Installed the Omarchy theme hook; no JetBrains 2024.1+ profiles found yet."
  exit
fi

archive=${OMARCHY_INTELLIJ_BRIDGE_ARCHIVE:-$CACHE_ARCHIVE}
if [[ ! -f $archive ]] || ! validate_archive "$archive"; then
  [[ $archive == "$CACHE_ARCHIVE" ]] || {
    echo "Invalid bridge archive: $archive" >&2
    exit 1
  }
  mkdir -p "$CACHE_DIR"
  download=$(mktemp "$CACHE_DIR/.bridge.XXXXXX")
  trap 'rm -f -- "$download"' EXIT
  if ! curl -fsSL "$RELEASE_ARCHIVE" -o "$download" || ! validate_archive "$download"; then
    notify "Could not install the IntelliJ theme bridge"
    echo "Could not download a valid bridge from $RELEASE_ARCHIVE" >&2
    exit 1
  fi
  mv -- "$download" "$CACHE_ARCHIVE"
  trap - EXIT
  archive=$CACHE_ARCHIVE
fi

extract_dir=$(mktemp -d)
trap 'rm -rf -- "$extract_dir"' EXIT
unzip -q "$archive" -d "$extract_dir"
payload="$extract_dir/$PLUGIN_NAME"
updated=0

for product_dir in "${product_dirs[@]}"; do
  target="$product_dir/$PLUGIN_NAME"
  if [[ -f $target/.omarchy-managed-version ]] &&
    [[ $(<"$target/.omarchy-managed-version") == "$VERSION" ]] &&
    compgen -G "$target/lib/*.jar" >/dev/null; then
    continue
  fi

  stage="$product_dir/.$PLUGIN_NAME.new.$$"
  previous="$product_dir/.$PLUGIN_NAME.old.$$"
  rm -rf -- "$stage" "$previous"
  cp -a -- "$payload" "$stage"
  printf '%s\n' "$VERSION" >"$stage/.omarchy-managed-version"
  [[ ! -e $target ]] || mv -- "$target" "$previous"
  mv -- "$stage" "$target"
  rm -rf -- "$previous"
  updated=1
  echo "Installed bridge into $product_dir"
done

if [[ -f $HOME/.local/state/omarchy/current/theme/colors.toml ]]; then
  "$SYNC_SCRIPT"
fi

if (( updated )); then
  notify "IntelliJ theme bridge installed — restart JetBrains IDEs once"
else
  echo "IntelliJ theme bridge $VERSION is already installed."
fi
