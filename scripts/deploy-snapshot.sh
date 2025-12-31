#!/usr/bin/env bash
set -euo pipefail

default_server_host="192.168.1.96"

if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [SERVER_IP_OR_HOST]" >&2
  exit 2
fi

server_host="${1:-$default_server_host}"
if [[ -z "$server_host" ]]; then
  server_host="$default_server_host"
fi
remote_user="nineones"
remote_dir="/home/nineones/Stocker/rest-api"
service_name="stocker-rest-api.service"
releases_dir="releases"
current_symlink="stocker-rest-api-current.jar"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}" )/.." && pwd)"
cd "$repo_root"

artifact_id="stocker-rest-api"
version="$(grep -A 2 "<artifactId>${artifact_id}</artifactId>" pom.xml | grep -oP "<version>\\K[^<]+" | head -1)"

if [[ -z "$artifact_id" || -z "$version" ]]; then
  echo "Could not determine artifactId/version from pom.xml" >&2
  exit 1
fi

local_jar="target/${artifact_id}-${version}.jar"
if [[ ! -f "$local_jar" ]]; then
  local_jar="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2- || true)"
fi

if [[ -z "$local_jar" || ! -f "$local_jar" ]]; then
  echo "Maven-built jar not found under target/. Expected: target/${artifact_id}-${version}.jar" >&2
  exit 1
fi

jar_name="$(basename "$local_jar")"
remote_jar_rel="$releases_dir/$jar_name"
remote_jar_tmp_rel="$releases_dir/$jar_name.new"

echo "Ensuring remote releases directory exists on $remote_user@$server_host"
ssh -o BatchMode=yes "$remote_user@$server_host" "set -euo pipefail; mkdir -p '$remote_dir/$releases_dir'"

echo "Uploading $local_jar -> $remote_user@$server_host:$remote_dir/$remote_jar_rel"
scp -o BatchMode=yes "$local_jar" "$remote_user@$server_host:$remote_dir/$remote_jar_tmp_rel"

echo "Updating symlink and restarting service on $remote_user@$server_host"
ssh -o BatchMode=yes "$remote_user@$server_host" "set -euo pipefail; \
	cd '$remote_dir'; \
	sudo systemctl stop '$service_name' || true; \
	mv '$remote_jar_tmp_rel' '$remote_jar_rel'; \
	chmod 700 '$remote_jar_rel'; \
	ln -sfn '$remote_jar_rel' '$current_symlink'; \
	sudo systemctl daemon-reload; \
	sudo systemctl enable '$service_name'; \
	sudo systemctl start '$service_name'"

echo "Deploy snapshot steps completed."
