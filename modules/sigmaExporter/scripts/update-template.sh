#!/usr/bin/env bash
# Regenerate the bundled viewer template (network.zip) from an InteractiveVis checkout.
#
# Usage: scripts/update-template.sh /path/to/InteractiveVis
#
# Zips everything under <checkout>/network/ into
# src/main/resources/uk/ac/ox/oii/sigmaexporter/resources/network.zip, with entries
# under a top-level "network/" folder (the exporter writes config.json and data.json
# into that folder). Left out:
#   network/config*.json, network/data/, network/server/, network/index_ukgov.html,
#   any node_modules/ folder, any *.md file, and dotfiles such as .DS_Store.
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 /path/to/InteractiveVis" >&2
    exit 2
fi

src="$(cd "$1" && pwd)"
if [ ! -d "$src/network" ]; then
    echo "error: $src/network not found (expected an InteractiveVis checkout)" >&2
    exit 1
fi
if [ ! -f "$src/network/index.html" ]; then
    echo "error: $src/network/index.html not found" >&2
    exit 1
fi
command -v zip >/dev/null 2>&1 || { echo "error: 'zip' is required" >&2; exit 1; }

module_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$module_dir/src/main/resources/uk/ac/ox/oii/sigmaexporter/resources/network.zip"

if ! grep -qi '</head>' "$src/network/index.html"; then
    echo "warning: network/index.html has no </head>; 'Embed data in index.html' will insert the data at <body> instead" >&2
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Sorted file list relative to the checkout, so the zip is stable between runs.
(
    cd "$src"
    find network \
        \( -path 'network/data' -o -path 'network/server' -o -name node_modules -o -name '.*' \) -prune -o \
        -type f \
        ! -path 'network/config*.json' \
        ! -path 'network/index_ukgov.html' \
        ! -name '*.md' \
        -print | LC_ALL=C sort
) > "$tmp/files.txt"

if [ ! -s "$tmp/files.txt" ]; then
    echo "error: no files to package" >&2
    exit 1
fi

# -X: no extra file attributes, -D: no directory entries (the exporter creates folders itself)
(cd "$src" && zip -q -X -D "$tmp/network.zip" -@ < "$tmp/files.txt")
mv "$tmp/network.zip" "$out"

echo "Wrote $out from $src/network ($(wc -l < "$tmp/files.txt" | tr -d ' ') files)"
if git -C "$src" rev-parse --short HEAD >/dev/null 2>&1; then
    echo "InteractiveVis commit: $(git -C "$src" rev-parse --short HEAD)"
fi
