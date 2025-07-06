#!/bin/bash

# Simple script to start webhook test server
# Usage: ./start-webhook-server.sh SECRET_FROM_API
# Example: ./start-webhook-server.sh "lleeG6fXfLmdgtvSTF_esaD75XUXsJSpmNUbHxP73mU"

set -e

if [ -z "$1" ]; then
    echo "❌ Please provide the webhook secret from your API configuration"
    echo ""
    echo "Usage: $0 SECRET_FROM_API"
    echo ""
    echo "Example:"
    echo "  $0 \"lleeG6fXfLmdgtvSTF_esaD75XUXsJSpmNUbHxP73mU\""
    echo ""
    echo "To get the secret:"
    echo "  1. Create webhook: POST /api/v1/webhooks"
    echo "  2. Copy the 'secret' field from response"
    echo "  3. Use it with this script"
    exit 1
fi

SECRET="$1"

# Check dependencies
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

echo "🚀 Starting webhook server with your secret..."
echo "🔑 Secret: ${SECRET:0:8}...${SECRET: -8}"
echo "📡 Server: http://localhost:3001"
echo "🎯 Webhook endpoint: http://localhost:3001/webhook"
echo ""
echo "Press Ctrl+C to stop"
echo ""

export WEBHOOK_SECRET="$SECRET"
node webhook-server.js