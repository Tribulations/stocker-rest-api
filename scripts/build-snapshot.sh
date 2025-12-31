#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

backup="$(mktemp)"
cp pom.xml "$backup"

restore_pom() {
  mv "$backup" pom.xml
  echo "Build failed; pom.xml version restored to $old_version"
}

echo "Incrementing minor version in pom.xml..."
bump_output="$(bash scripts/bump-minor-version.sh)"
old_version="$(printf '%s\n' "$bump_output" | awk -F= '/^old_version=/{print $2}' | tail -1)"
new_version="$(printf '%s\n' "$bump_output" | awk -F= '/^new_version=/{print $2}' | tail -1)"
if [[ -z "$old_version" || -z "$new_version" ]]; then
  mv "$backup" pom.xml
  echo "Could not determine bumped version from bump-minor-version.sh" >&2
  exit 1
fi

echo "Version updated from $old_version to $new_version"
echo "Running Maven clean install..."

if ./mvnw clean install; then
  rm -f "$backup"
  git add pom.xml
  git commit -m "Bump minor version"
else
  restore_pom
  exit 1
fi
