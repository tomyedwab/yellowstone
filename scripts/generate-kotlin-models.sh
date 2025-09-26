#!/bin/bash

# Script to generate Kotlin models from YAML schema

set -e

# Check for required arguments
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input_yaml> <output_kotlin>"
    exit 1
fi

INPUT_YAML="$1"
OUTPUT_KOTLIN="$2"

echo "Generating Kotlin models from schema..."

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
    exit 1
fi

echo "Using Go binary: $GO_BIN"

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Change to scripts directory and run the Go generator
cd "$SCRIPT_DIR"
"$GO_BIN" run generate-kotlin-models.go "$INPUT_YAML" "$OUTPUT_KOTLIN"