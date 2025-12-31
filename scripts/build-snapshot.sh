#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

echo "Incrementing minor version in pom.xml..."

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

backup="$(mktemp)"
cp pom.xml "$backup"

restore_pom() {
  mv "$backup" pom.xml
  echo "Build failed; pom.xml version restored to $old_version"
}

sed -i "/<artifactId>stocker-rest-api<\\/artifactId>/,/<\\/version>/ s/<version>${old_version}<\\/version>/<version>${new_version}<\\/version>/" pom.xml

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
