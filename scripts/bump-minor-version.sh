#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

old_version="$(grep -A 2 "<artifactId>stocker-rest-api</artifactId>" pom.xml | grep -oP "<version>\\K[0-9]+\\.[0-9]+-SNAPSHOT" | head -1)"
if [[ -z "$old_version" ]]; then
  echo "Could not determine current project version from pom.xml" >&2
  exit 1
fi

old_version_base="${old_version%-SNAPSHOT}"
major="$(echo "$old_version_base" | cut -d. -f1)"
minor="$(echo "$old_version_base" | cut -d. -f2)"
new_minor=$((minor + 1))
new_version="$major.$new_minor-SNAPSHOT"

sed -i "/<artifactId>stocker-rest-api<\\/artifactId>/,/<\\/version>/ s/<version>${old_version}<\\/version>/<version>${new_version}<\\/version>/" pom.xml

echo "old_version=$old_version"
echo "new_version=$new_version"
