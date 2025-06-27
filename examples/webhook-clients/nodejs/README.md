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

### 1. Install Dependencies
```bash
npm install
```

### 2. Start Server
```bash
# Default mode
npm start

# Development mode with auto-reload  
npm run dev

# With custom secret
WEBHOOK_SECRET=your-webhook-secret npm start

# Custom port
PORT=3002 npm start
```

### 3. Open Web Interface
Visit http://localhost:3001 to see the real-time dashboard

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
npm start

# In another terminal, create webhook in Screenshot API
curl -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "http://localhost:3001/webhook",
    "events": ["SCREENSHOT_COMPLETED"],
    "description": "Test webhook integration"
  }'

# Test the webhook
curl -X POST http://localhost:8080/api/v1/webhooks/WEBHOOK_ID/test \
  -H "Authorization: Bearer YOUR_JWT"
```

### 2. Retry Logic Testing
```bash
# Use error endpoint to force retries
curl -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "http://localhost:3001/webhook/test-error",
    "events": ["SCREENSHOT_COMPLETED"],
    "description": "Test retry logic"
  }'
```

### 3. Intermittent Failure Testing
```bash
# Use random endpoint for realistic failure scenarios
curl -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "http://localhost:3001/webhook/test-random",
    "events": ["SCREENSHOT_COMPLETED"],
    "description": "Test random failures"
  }'
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

## Monitoring Integration

### JSON Stats Endpoint
`GET /stats` returns monitoring-friendly JSON:
```json
{
  "total_webhooks": 42,
  "valid_signatures": 40,
  "invalid_signatures": 2,
  "success_rate": 0.95,
  "uptime_seconds": 3600,
  "last_webhook": "2023-12-01T10:30:00Z"
}
```

### Health Check
`GET /health` returns simple status for load balancers:
```json
{
  "status": "healthy",
  "uptime": 3600,
  "port": 3001
}
```

## Development Tips

### Debug Mode
Enable verbose logging:
```bash
DEBUG=* npm start
```

### Custom Secret Management
For testing with different secrets:
```bash
# Create webhook with custom secret in Screenshot API
WEBHOOK_SECRET=your-custom-secret npm start
```

### Testing with ngrok
For external webhook testing:
```bash
# Install ngrok
npm install -g ngrok

# Start webhook server
npm start

# In another terminal, expose to internet
ngrok http 3001

# Use ngrok URL in webhook configuration
# https://abc123.ngrok.io/webhook
```

## Troubleshooting

### Common Issues

**Invalid Signatures**
- Verify WEBHOOK_SECRET matches the one from webhook creation
- Ensure request body is raw bytes, not parsed JSON
- Check that no middleware is modifying the request

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
- `test-webhook-flow.sh` - Automated testing script