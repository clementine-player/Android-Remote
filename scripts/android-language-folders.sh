#!/usr/bin/env bash
# Renames the folders `tx pull` makes for languages .tx/config doesn't map, from Transifex's
# language codes to Android's resource qualifiers, which won't build otherwise:
#
#   values-zh_CN     -> values-zh-rCN
#   values-zh_Hans   -> values-b+zh+Hans
#   values-es_419    -> values-b+es+419
#   values-sr@latin  -> values-b+sr+Latn
#
# Run by .github/workflows/translations.yml after `tx pull`; it can be run again safely.
set -euo pipefail

cd "$(dirname "$0")/../app/src/main/res"

for dir in values-*[_@]*/; do
  [ -d "$dir" ] || continue
  dir=${dir%/}
  code=${dir#values-}
  case "$code" in
    *@latin) code="${code%@latin}_Latn" ;;
    *@cyrillic) code="${code%@cyrillic}_Cyrl" ;;
    *@*) echo "::error::Don't know Android's name for the language $code ($dir)." >&2; exit 1 ;;
  esac
  if [[ $code =~ ^([a-z]{2,3})_([A-Z]{2})$ ]]; then
    qualifier="${BASH_REMATCH[1]}-r${BASH_REMATCH[2]}"
  else
    qualifier="b+${code//_/+}"
  fi
  mkdir -p "values-$qualifier"
  mv "$dir"/* "values-$qualifier/"
  rmdir "$dir"
  echo "$dir -> values-$qualifier"
done
