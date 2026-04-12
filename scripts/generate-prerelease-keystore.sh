#!/bin/bash
# Run once to generate a prerelease keystore and print the base64 for GitHub secrets.
# Usage: bash scripts/generate-prerelease-keystore.sh

set -e

KEYSTORE_FILE="prerelease.keystore"
ALIAS="neomovies-prerelease"
STORE_PASS="neomovies-prerelease-store"
KEY_PASS="neomovies-prerelease-key"

keytool -genkeypair \
  -keystore "$KEYSTORE_FILE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=NeoMovies Prerelease, O=NeoMovies, C=RU"

echo ""
echo "=== Add these to GitHub repository secrets ==="
echo "RELEASE_KEYSTORE_BASE64:"
base64 -i "$KEYSTORE_FILE"
echo ""
echo "RELEASE_KEYSTORE_PASSWORD: $STORE_PASS"
echo "RELEASE_KEY_ALIAS: $ALIAS"
echo "RELEASE_KEY_PASSWORD: $KEY_PASS"
echo ""
echo "Then delete $KEYSTORE_FILE (it's stored in secrets now)"
