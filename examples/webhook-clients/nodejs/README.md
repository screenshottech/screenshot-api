# Node.js Webhook Client Example

A complete Node.js webhook server for testing webhook deliveries from the Screenshot API.

## Features

- ✅ **HMAC Signature Verification** - Validates webhook authenticity using HMAC-SHA256
- 📊 **Real-time Dashboard** - Web interface showing webhook statistics and history
- 🌐 **Web Interface** - Visual dashboard at http://localhost:3001
- 🔍 **Detailed Logging** - Full webhook payload inspection with timestamps
- 🧪 **Multiple Test Endpoints** - Different endpoints for testing various scenarios
- 📋 **History Tracking** - Stores and displays recent webhook deliveries
- ⚡ **Real-time Updates** - Live statistics and webhook display

## Quick Start

### Option 1: Using the helper script (Recommended)

1. Get your webhook secret from the Screenshot API
2. Run the setup script:

```bash
./start-webhook-server.sh "lleeG6fXfLmdgtvSTF_esaD75XUXsJSpmNUbHxP73mU"
```

### Option 2: Manual Setup

#### 1. Install Dependencies
```bash
npm install
```

#### 2. Start Server with Secret
```bash
# With your webhook secret from the API
WEBHOOK_SECRET="lleeG6fXfLmdgtvSTF_esaD75XUXsJSpmNUbHxP73mU" node webhook-server.js

# Or set environment variable
export WEBHOOK_SECRET="lleeG6fXfLmdgtvSTF_esaD75XUXsJSpmNUbHxP73mU"
node webhook-server.js
```

#### 3. Open Web Interface
Visit http://localhost:3001 to see the real-time dashboard

## How to Get Your Webhook Secret

1. Create a webhook using the Screenshot API:
```bash
curl -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "http://localhost:3001/webhook",
    "events": ["WEBHOOK_TEST", "SCREENSHOT_COMPLETED"],
    "description": "Test webhook"
  }'
```

2. Copy the `secret` field from the response:
```json
{
  "id": "webhook-id",
  "secret": "lleeG6fXfLmdgtvSTF_esaD75XUXsJSpmNUbHxP73mU",
  "url": "http://localhost:3001/webhook",
  ...
}
```

3. Use that secret to start the webhook server

## API Endpoints

### Webhook Receivers
- `POST /webhook` - Main webhook endpoint with full HMAC verification
- `POST /webhook/test-success` - Always returns 200 OK (for testing success scenarios)
- `POST /webhook/test-error` - Always returns 500 Error (for testing retry logic)
- `POST /webhook/test-random` - Randomly returns success or error (for intermittent testing)

### Management & Monitoring
- `GET /` - Real-time web dashboard
- `GET /health` - Health check endpoint
- `GET /stats` - JSON statistics (for monitoring integration)
- `GET /webhooks?limit=N` - Recent webhook history (default limit: 10)
- `DELETE /webhooks` - Clear webhook history

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3001` | Server port |
| `WEBHOOK_SECRET` | `default-test-secret` | HMAC secret for signature verification |

## Testing Scenarios

### 1. Basic Webhook Integration
```bash
# Start the webhook server
./start-webhook-server.sh "YOUR_SECRET_HERE"

# Test the webhook
curl -X POST http://localhost:8080/api/v1/webhooks/WEBHOOK_ID/test \
  -H "Authorization: Bearer YOUR_JWT"
```

### 2. Retry Logic Testing
```bash
# Use error endpoint to force retries - configure webhook with:
"url": "http://localhost:3001/webhook/test-error"
```

### 3. Intermittent Failure Testing
```bash
# Use random endpoint for realistic failure scenarios - configure webhook with:
"url": "http://localhost:3001/webhook/test-random"
```

## Security Implementation

### HMAC Signature Verification
The server implements proper HMAC-SHA256 signature verification:

```javascript
function verifySignature(payload, signature, secret) {
  const expectedSignature = crypto
    .createHmac('sha256', secret)
    .update(payload, 'utf8')
    .digest('hex');
  
  const receivedSignature = signature.replace('sha256=', '');
  
  return crypto.timingSafeEqual(
    Buffer.from(expectedSignature, 'hex'),
    Buffer.from(receivedSignature, 'hex')
  );
}
```

### Required Headers
The server checks for these webhook headers:
- `X-Webhook-Signature-256` - HMAC signature (required)
- `X-Webhook-Event` - Event type (logged)
- `X-Webhook-Delivery` - Unique delivery ID (used for deduplication)
- `User-Agent` - Should be "ScreenshotAPI-Webhook/1.0"

## Dashboard Features

The web interface displays:
- **Real-time Statistics**: Total webhooks, valid vs invalid signatures
- **Server Status**: Uptime, port, current configuration
- **Webhook History**: Recent deliveries with full details
- **Event Breakdown**: Statistics by event type
- **Response Times**: Performance metrics
- **Error Analysis**: Failed delivery details

## Troubleshooting

### Common Issues

**Invalid Signatures**
- Verify WEBHOOK_SECRET matches the one from webhook creation response
- The secret should be used as-is (URL-safe Base64 string)
- Ensure request body is raw bytes, not parsed JSON

**Connection Refused**
- Verify server is running: `curl http://localhost:3001/health`
- Check port availability: `lsof -i :3001`
- For external access, use ngrok or similar tunneling

**Webhooks Not Received**
- Check Screenshot API logs for delivery attempts
- Verify webhook URL is accessible from Screenshot API
- Ensure webhook is active and configured for correct events

### Debug Commands
```bash
# Check if server is running
curl http://localhost:3001/health

# View current statistics
curl http://localhost:3001/stats

# Check recent webhooks
curl http://localhost:3001/webhooks

# Clear history for fresh testing
curl -X DELETE http://localhost:3001/webhooks
```

## Production Notes

⚠️ **This is a development example only**. For production use:

1. **Use HTTPS endpoints** - Never use HTTP in production
2. **Implement rate limiting** - Protect against abuse
3. **Add authentication** - Beyond just HMAC verification
4. **Store secrets securely** - Use environment variables or secret management
5. **Add monitoring** - Implement proper logging and alerting
6. **Validate input** - Thoroughly validate all webhook data
7. **Handle errors gracefully** - Don't expose internal error details

## Files

- `package.json` - Node.js dependencies and scripts
- `webhook-server.js` - Main webhook server implementation  
- `start-webhook-server.sh` - Helper script to start server with secret
- `README.md` - This documentation