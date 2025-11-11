#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="/usr/local/bin"
SCRIPT_NAME="jeap"
SCRIPT_URL="https://raw.githubusercontent.com/jeap-admin-ch/jeap-cli/refs/heads/main/jeap"

echo "Installing JEAP CLI launcher to $INSTALL_DIR/$SCRIPT_NAME ..."

# Ensure dependencies
command -v curl >/dev/null 2>&1 || { echo "curl is required but not installed"; exit 1; }
command -v sudo >/dev/null 2>&1 || { echo "sudo is required but not installed"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker is required but not installed"; exit 1; }

# Download launcher script
TMP_FILE="$(mktemp)"
curl -fsSL "$SCRIPT_URL" -o "$TMP_FILE"

# Make it executable and move it to the install directory
chmod +x "$TMP_FILE"
sudo mv "$TMP_FILE" "$INSTALL_DIR/$SCRIPT_NAME"

echo "✅ Installed successfully: $INSTALL_DIR/$SCRIPT_NAME"
echo "You can now run it using: $SCRIPT_NAME [args]"

jeap help
