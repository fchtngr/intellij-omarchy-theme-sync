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
SHARE_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/omarchy-intellij"
REAL_SCRIPT="$SHARE_DIR/omarchy-intellij-theme-sync.py"

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

verify_release_archive() {
  local archive=$1 checksum
  checksum=$(sha256sum -- "$archive") || return 1
  [[ ${checksum%% *} == "$BRIDGE_SHA256" ]] && validate_archive "$archive"
}

# Collect all relevant JetBrains product directories
collect_product_dirs() {
  product_dirs=()

  # Standard Linux plugins location
  if [[ -d $JETBRAINS_DIR ]]; then
    for product_dir in "$JETBRAINS_DIR"/*/; do
      [[ -d $product_dir ]] || continue
      [[ $(basename "$product_dir") == "Toolbox" ]] && continue
      product_dirs+=("$product_dir")
    done
  fi

  # Modern Toolbox plugins location
  local TOOLBOX_APPS="$JETBRAINS_DIR/Toolbox/apps"
  if [[ -d $TOOLBOX_APPS ]]; then
    for app_dir in "$TOOLBOX_APPS"/*/; do
      [[ -d $app_dir ]] || continue
      if [[ -d "$app_dir/plugins" ]]; then
        product_dirs+=("$app_dir/plugins")
      else
        product_dirs+=("$app_dir")
      fi
    done
  fi

  # Remove duplicates
  local -A seen=()
  local unique=()
  local d
  for d in "${product_dirs[@]}"; do
    if [[ -z ${seen[$d]+x} ]]; then
      seen[$d]=1
      unique+=("$d")
    fi
  done
  product_dirs=("${unique[@]}")
}

uninstall() {
  rm -f -- "$HOOK_PATH"
  rm -f -- "$REAL_SCRIPT"

  collect_product_dirs

  local product_dir target
  for product_dir in "${product_dirs[@]}"; do
    target="$product_dir/$PLUGIN_NAME"
    [[ -f $target/.omarchy-managed-version ]] || continue
    rm -rf -- "$target"
    echo "Removed bridge from $product_dir"
  done

  rm -f -- "$CACHE_ARCHIVE"
  echo "Removed the Omarchy IntelliJ theme hook and managed bridges."
}

if [[ ${1:-} == "--uninstall" ]]; then
  uninstall
  exit
fi

# Install the real Python script
install -Dm755 "$SYNC_SCRIPT" "$REAL_SCRIPT"

# Install a shell wrapper as the actual hook (works even when Omarchy uses "sh")
install -d "$(dirname "$HOOK_PATH")"
cat > "$HOOK_PATH" << EOF
#!/bin/bash
exec python3 "$REAL_SCRIPT" "\$@"
EOF
chmod 755 "$HOOK_PATH"

collect_product_dirs

if (( ${#product_dirs[@]} == 0 )); then
  echo "Installed the Omarchy theme hook. No JetBrains product directories found yet."
  exit
fi

archive=${OMARCHY_INTELLIJ_BRIDGE_ARCHIVE:-$CACHE_ARCHIVE}
archive_validator=validate_archive
if [[ $archive == "$CACHE_ARCHIVE" ]]; then
  BRIDGE_SHA256=$(jq -er '.bridgeSha256 | select(type == "string" and test("^[0-9a-f]{64}$"))' "$PLUGIN_DIR/manifest.json") || {
    echo "Missing or invalid bridgeSha256 in manifest.json" >&2
    exit 1
  }
  archive_validator=verify_release_archive
fi

if [[ ! -f $archive ]] || ! "$archive_validator" "$archive"; then
  [[ $archive == "$CACHE_ARCHIVE" ]] || {
    echo "Invalid bridge archive: $archive" >&2
    exit 1
  }
  mkdir -p "$CACHE_DIR"
  download=$(mktemp "$CACHE_DIR/.bridge.XXXXXX")
  trap 'rm -f -- "$download"' EXIT
  if ! curl -fsSL "$RELEASE_ARCHIVE" -o "$download" || ! verify_release_archive "$download"; then
    notify "Could not install the IntelliJ theme bridge"
    echo "Could not download a valid, checksum-matching bridge from $RELEASE_ARCHIVE" >&2
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
  "$REAL_SCRIPT"
fi

if (( updated )); then
  notify "IntelliJ theme bridge installed — restart JetBrains IDEs once"
else
  echo "IntelliJ theme bridge $VERSION is already installed."
fi
