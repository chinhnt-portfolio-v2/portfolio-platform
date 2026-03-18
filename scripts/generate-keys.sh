#!/bin/bash
# Generate RSA key pair for JWT RS256 signing
# Run this script from the project root directory

set -e

KEYS_DIR="src/main/resources/keys"

echo "Generating RSA key pair for JWT RS256..."

# Create keys directory if it doesn't exist
mkdir -p "$KEYS_DIR"

# Generate 2048-bit RSA private key
openssl genrsa -out "$KEYS_DIR/private.pem" 2048

# Extract public key
openssl rsa -in "$KEYS_DIR/private.pem" -pubout -out "$KEYS_DIR/public.pem"

echo "Keys generated successfully:"
echo "  Private key: $KEYS_DIR/private.pem"
echo "  Public key: $KEYS_DIR/public.pem"
echo ""
echo "IMPORTANT: private.pem is in .gitignore — NEVER commit it!"
