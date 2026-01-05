#!/usr/bin/env bash
# Shebang: finds bash in system PATH and runs script with it (more portable than #!/bin/bash)

set -euo pipefail
# e: exit immediately if any command fails
# u: exit if undefined variable is used
# o pipefail: detect failures in pipes (e.g., cmd1 | cmd2)

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Get script's directory, go up one level (..), print full path
# ${BASH_SOURCE[0]} = path to this script file
# dirname = gets directory containing the script
# cd .. && pwd = go up one level and print absolute path
# Result: repo_root contains repository root directory path

cd "$repo_root"
# Change to repository root directory

pre_head="$(git rev-parse HEAD)"
# Save current git commit hash before making changes
# git rev-parse HEAD = gets the current commit's unique identifier

backup="$(mktemp)"
# Create temporary file with unique name, store path in $backup
# Used to backup pom.xml in case we need to restore it

cp pom.xml "$backup"
# Copy pom.xml to the temporary backup file

restore_pom() {
  mv "$backup" pom.xml
  echo "Build failed; pom.xml version restored to $old_version"
}
# Define function to restore pom.xml from backup
# mv = move/rename (replaces pom.xml with backup)
# Called if build fails

echo "Incrementing minor version in pom.xml..."
# Print status message to user

bump_output="$(bash scripts/bump-minor-version.sh)"
# Run version bump script, capture all its output in variable
# $() = command substitution (runs command, stores output)

old_version="$(printf '%s\n' "$bump_output" | awk -F= '/^old_version=/{print $2}' | tail -1)"
# Extract old version from bump script output:
# printf '%s\n' "$bump_output" = print output line by line
# awk -F= = use = as field separator
# /^old_version=/ = find lines starting with "old_version="
# {print $2} = print second field (value after =)
# tail -1 = take last matching line

new_version="$(printf '%s\n' "$bump_output" | awk -F= '/^new_version=/{print $2}' | tail -1)"
# Same as above but extracts new_version value

if [[ -z "$old_version" || -z "$new_version" ]]; then
# Check if either version variable is empty
# -z = "zero length" test (true if string is empty)
# || = logical OR

  mv "$backup" pom.xml
  # Restore original pom.xml from backup
  
  echo "Could not determine bumped version from bump-minor-version.sh" >&2
  # Print error to stderr (>&2)
  
  exit 1
  # Exit with error code 1 (failure)
fi

echo "Version updated from $old_version to $new_version"
# Print success message with version numbers

echo "Running Maven clean install..."
# Print status message

if ./mvnw clean install; then
# Run Maven build (./mvnw = Maven wrapper in current directory)
# if command succeeds (exit code 0), execute then block

  git add pom.xml
  # Stage the modified pom.xml for commit
  
  git commit -m "Bump minor version"
  # Create git commit with message
  # -m = message flag
  
  current_branch="$(git rev-parse --abbrev-ref HEAD)"
  # Get current git branch name
  # --abbrev-ref HEAD = short name of current branch
  
  if [[ "$current_branch" != "main" ]]; then
  # Check if current branch is NOT main
  # != = not equal
  
    mv "$backup" pom.xml
    # Restore backup
    
    git reset --hard "$pre_head"
    # Reset git to state before any changes
    # --hard = discard all changes
    # "$pre_head" = commit hash saved earlier
    
    echo "Refusing to push: current branch is '$current_branch' (expected 'main')" >&2
    # Error message explaining why push was refused
    
    exit 1
    # Exit with error
  fi
  
  if git push origin main; then
  # Push changes to remote repository
  # origin = default remote name
  # main = branch name
  
    rm -f "$backup"
    # Delete backup file (no longer needed)
    # -f = force (don't error if file doesn't exist)
  else
    mv "$backup" pom.xml
    # Restore backup if push failed
    
    git reset --hard "$pre_head"
    # Undo local commit
    
    echo "git push failed; reverted version bump and local commit" >&2
    # Error message
    
    exit 1
    # Exit with error
  fi
else
  restore_pom
  # If Maven build failed, call restore function
  
  exit 1
  # Exit with error
fi
