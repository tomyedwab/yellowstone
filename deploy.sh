#!/bin/bash

set -e

# Abort if there are uncommitted changes
if ! git diff --exit-code; then
  echo "There are uncommitted changes. Please commit or stash them before running this script."
  exit 1
fi

# This script fetches the version number from frontend/pubspec.yaml, increments
# it, and creates a tag in the git repository.

# Get the current version number from pubspec.yaml
CURRENT_VERSION=$(grep -oP 'version: \K\d+\.\d+\.\d+' frontend/pubspec.yaml)

# Increment the version number
NEW_VERSION=$(echo $CURRENT_VERSION | awk -F. -v OFS=. '{$NF++; print}')

# Update pubspec.yaml with the new version number
sed -i "s/version: $CURRENT_VERSION/version: $NEW_VERSION/" frontend/pubspec.yaml

# Update main.go with the new version number
sed -i "s/const Version = \".*\"/const Version = \"$NEW_VERSION\"/" main.go

echo "Building new version: $NEW_VERSION"

# Build frontends
docker build -t tomyedwab/yellowstone-arm64:${NEW_VERSION} -f Dockerfile.arm64 . && \
  docker push tomyedwab/yellowstone-arm64:${NEW_VERSION}

pushd frontend && \
  flutter build web && \
  flutter build apk && \
  popd

echo "Tagging new version: $NEW_VERSION"

# Commit the changes to the main branch
git add frontend/pubspec.yaml main.go
git commit -m "Bump version to $NEW_VERSION"
git push

git tag -a v$NEW_VERSION -m "Release version $NEW_VERSION"
git push origin v$NEW_VERSION
