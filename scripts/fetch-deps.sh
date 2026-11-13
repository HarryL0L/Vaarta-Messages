#!/usr/bin/env bash
#
# Fetches the AOSP libraries Vaarta Messages is built against into third_party/.
#
# Inside an Android source tree these come from the tree itself (see
# Android.bp). The standalone Gradle build needs its own copies, pinned here
# to the android16-release commits the app is known to build with.

set -euo pipefail

BASE_URL="https://android.googlesource.com/platform"
DEST="$(cd "$(dirname "$0")/.." && pwd)/third_party"

# name | repository | commit
DEPS=(
    "chips|frameworks/opt/chips|5660c2f1d6c73c0ea7139e394cbd25ac3ab90f39"
    "photoviewer|frameworks/opt/photoviewer|fb5c100b4a415e69e507962dd6c32660d71dfc66"
    "colorpicker|frameworks/opt/colorpicker|34d5137f881a0ffc9f6bb93c5d2e5bbe99265b00"
    "vcard|frameworks/opt/vcard|09192f417d6f105cee940f566c0daa0db5fddb20"
    "giflib|external/giflib|ad011248e266cc1bad1489704cc214942231cf09"
)

mkdir -p "$DEST"
for dep in "${DEPS[@]}"; do
    IFS='|' read -r name repo commit <<< "$dep"
    dir="$DEST/$name"
    if [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null || true)" = "$commit" ]; then
        echo "$name: up to date"
        continue
    fi
    echo "$name: fetching $commit"
    rm -rf "$dir"
    git init -q "$dir"
    git -C "$dir" remote add origin "$BASE_URL/$repo"
    git -C "$dir" fetch -q --depth 1 origin "$commit"
    git -C "$dir" checkout -q FETCH_HEAD
done
