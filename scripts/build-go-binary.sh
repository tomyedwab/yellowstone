#!/bin/bash

# Build script for Yellowstone Go binary (Linux amd64) and MD5 generation

set -e

echo "Building Yellowstone Go binary for Linux amd64..."

# Try to find Go binary in common locations
GO_BIN=""
if command -v go >/dev/null 2>&1; then
    GO_BIN="go"
elif [ -f "/usr/local/go/bin/go" ]; then
    GO_BIN="/usr/local/go/bin/go"
elif [ -f "$HOME/go/bin/go" ]; then
    GO_BIN="$HOME/go/bin/go"
elif [ -f "/usr/bin/go" ]; then
    GO_BIN="/usr/bin/go"
else
    echo "Error: Go binary not found. Please ensure Go is installed."
    echo "Checked locations:"
    echo "  - PATH (command -v go)"
    echo "  - /usr/local/go/bin/go"
    echo "  - $HOME/go/bin/go"
    echo "  - /usr/bin/go"
    exit 1
fi

echo "Using Go binary: $GO_BIN"

# Set build environment for Linux amd64
export GOOS=linux
export GOARCH=amd64

# Create build output directory
BUILD_DIR="$(dirname "$0")/../build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/pkg/bin"

# Build the Go binary
"$GO_BIN" build -o "$BUILD_DIR/pkg/bin/app" ./main.go
cp manifest.json "$BUILD_DIR/pkg/"

# Create an archive
cd "$BUILD_DIR/pkg"
zip -r ../tasks.zip .
cd ..

# Generate MD5 hash
md5sum tasks.zip | cut -d' ' -f1 > tasks.md5

echo "Build complete:"
echo "  Binary: $BUILD_DIR/tasks.zip"
echo "  MD5: $BUILD_DIR/tasks.md5"
echo "  MD5 Hash: $(cat tasks.md5)"
