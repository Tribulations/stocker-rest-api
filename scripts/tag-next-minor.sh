#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

pattern='v[0-9]*.[0-9]*'

latest_tag="$(git tag -l "$pattern" --sort=-v:refname | head -1 || true)"
if [[ -z "$latest_tag" ]]; then
  major=0
  minor=0
else
  if [[ ! "$latest_tag" =~ ^v([0-9]+)\.([0-9]+)$ ]]; then
    echo "Latest tag '$latest_tag' does not match v<major>.<minor>" >&2
    exit 1
  fi
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
fi

next_minor=$((minor + 1))
new_tag="v${major}.${next_minor}"

if git rev-parse -q --verify "refs/tags/$new_tag" >/dev/null; then
  echo "Tag already exists: $new_tag" >&2
  exit 1
fi

echo "Creating tag $new_tag (previous: ${latest_tag:-none})"
git tag "$new_tag"

echo "Pushing tag $new_tag to origin"
git push origin "$new_tag"
