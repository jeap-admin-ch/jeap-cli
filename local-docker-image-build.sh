#!/bin/bash
set -e

# Script to build jeap-cli Docker image with proxy support
# Reads proxy configuration from standard Linux environment variables

echo "========================================="
echo "jEAP CLI Docker Image Build Script"
echo "========================================="
echo ""

# Use uppercase env vars if set, otherwise fallback to lowercase
HTTP_PROXY_VAR="${HTTP_PROXY:-${http_proxy}}"
HTTPS_PROXY_VAR="${HTTPS_PROXY:-${https_proxy}}"
NO_PROXY_VAR="${NO_PROXY:-${no_proxy}}"

# Display proxy configuration
if [ -n "$HTTP_PROXY_VAR" ] || [ -n "$HTTPS_PROXY_VAR" ]; then
    echo "Proxy configuration detected:"
    [ -n "$HTTP_PROXY_VAR" ] && echo "  HTTP_PROXY:  $HTTP_PROXY_VAR"
    [ -n "$HTTPS_PROXY_VAR" ] && echo "  HTTPS_PROXY: $HTTPS_PROXY_VAR"
    [ -n "$NO_PROXY_VAR" ] && echo "  NO_PROXY:    $NO_PROXY_VAR"
    echo ""
else
    echo "No proxy configuration detected. Building without proxy."
    echo ""
fi

# Step 1: Build native executable
echo "Step 1/2: Building native executable..."
echo "----------------------------------------"
./mvnw package -Pnative

echo ""
echo "Step 2/2: Building Docker image..."
echo "----------------------------------------"

# Build Docker image with or without proxy args
DOCKER_BUILD_ARGS=()
if [ -n "$HTTP_PROXY_VAR" ]; then
    DOCKER_BUILD_ARGS+=(--build-arg "HTTP_PROXY=$HTTP_PROXY_VAR")
fi
if [ -n "$HTTPS_PROXY_VAR" ]; then
    DOCKER_BUILD_ARGS+=(--build-arg "HTTPS_PROXY=$HTTPS_PROXY_VAR")
fi
if [ -n "$NO_PROXY_VAR" ]; then
    DOCKER_BUILD_ARGS+=(--build-arg "NO_PROXY=$NO_PROXY_VAR")
fi

# Check for CA certificate bundle and add as secret if it exists
CA_BUNDLE_PATH="/etc/ssl/certs/ca-certificates.crt"
if [ -f "$CA_BUNDLE_PATH" ]; then
    echo "Found CA certificate bundle at $CA_BUNDLE_PATH"
    DOCKER_BUILD_ARGS+=(--secret "id=cacert,src=$CA_BUNDLE_PATH")
fi

if [ ${#DOCKER_BUILD_ARGS[@]} -gt 0 ]; then
    echo "Building with additional configuration..."
    docker build "${DOCKER_BUILD_ARGS[@]}" -t jeap-cli:latest jeap-cli/
else
    echo "Building with defaults..."
    docker build -t jeap-cli:latest jeap-cli/
fi

echo ""
echo "========================================="
echo "Build completed successfully!"
echo "========================================="
echo ""
echo "Image: jeap-cli:latest"
echo ""
echo "Run the CLI with:"
echo "  export JEAP_CLI_IMAGE=jeap-cli:latest && ./jeap help"
