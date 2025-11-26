#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="jeap"
SCRIPT_URL="https://raw.githubusercontent.com/jeap-admin-ch/jeap-cli/refs/heads/main/jeap"

GLOBAL_INSTALL_DIR="/usr/local/bin"
USER_INSTALL_DIR="${HOME}/.local/bin"

echo "JEAP CLI Launcher Installer"
echo
echo "Where would you like to install the launcher?"
echo "  1) Global (for all users, requires sudo) -> ${GLOBAL_INSTALL_DIR}"
echo "  2) User only (current user)             -> ${USER_INSTALL_DIR}"
echo

read -r -p "Choice [2]: " INSTALL_CHOICE < /dev/tty

case "${INSTALL_CHOICE:-2}" in
  1)
    INSTALL_DIR="${GLOBAL_INSTALL_DIR}"
    INSTALL_SCOPE="global"
    ;;
  2|"")
    INSTALL_DIR="${USER_INSTALL_DIR}"
    INSTALL_SCOPE="user"
    ;;
  *)
    echo "Invalid choice. Please select 1 or 2."
    exit 1
    ;;
esac

echo
echo "Installing JEAP CLI launcher to ${INSTALL_DIR}/${SCRIPT_NAME} ..."
echo

# Ensure dependencies
command -v curl >/dev/null 2>&1 || { echo "curl is required but not installed"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker is required but not installed"; exit 1; }

# For global installation: ensure sudo is available
if [[ "${INSTALL_SCOPE}" == "global" ]]; then
  command -v sudo >/dev/null 2>&1 || { echo "sudo is required for global installation but not installed"; exit 1; }

  # Do not create the global install directory, just fail if it does not exist
  if [[ ! -d "${INSTALL_DIR}" ]]; then
    echo "Global install directory ${INSTALL_DIR} does not exist."
    echo "Please create it manually and rerun the installer, or choose user installation."
    exit 1
  fi
else
  # For user installation, create the directory if needed (no sudo)
  mkdir -p "${INSTALL_DIR}"
fi

# Download launcher script
TMP_FILE="$(mktemp)"
curl -fsSL "${SCRIPT_URL}" -o "${TMP_FILE}"

# Make it executable and move it to the install directory
chmod +x "${TMP_FILE}"

if [[ "${INSTALL_SCOPE}" == "global" ]]; then
  sudo mv "${TMP_FILE}" "${INSTALL_DIR}/${SCRIPT_NAME}"
else
  mv "${TMP_FILE}" "${INSTALL_DIR}/${SCRIPT_NAME}"
fi

echo "✅ Installed successfully: ${INSTALL_DIR}/${SCRIPT_NAME}"

# PATH hint for user installation
if [[ "${INSTALL_SCOPE}" == "user" ]]; then
  case ":${PATH}:" in
    *:"${INSTALL_DIR}":*)
      # Already in PATH
      ;;
    *)
      echo
      echo "Note: ${INSTALL_DIR} is currently not in your PATH."
      echo "Add the following line to your shell configuration (e.g. ~/.bashrc or ~/.zshrc):"
      echo "  export PATH=\"${INSTALL_DIR}:\$PATH\""
      ;;
  esac
fi

echo
echo "You can now run it using: ${SCRIPT_NAME} [args]"
echo

# Run CLI to test the installation
"${INSTALL_DIR}/${SCRIPT_NAME}" help
