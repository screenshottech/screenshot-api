const express = require('express');
const crypto = require('crypto');
const cors = require('cors');
const morgan = require('morgan');

const app = express();
const PORT = process.env.PORT || 3001;

// Middleware
app.use(cors());
app.use(morgan('combined'));
app.use(express.raw({type: 'application/json'}));

// Storage for received webhooks (in memory for testing)
const receivedWebhooks = [];
let webhookCount = 0;

// HMAC signature verification
function verifySignature(payload, signature, secret) {
  if (!signature || !secret) {
    return false;
  }

  try {
    const expectedSignature = crypto
      .createHmac('sha256', secret)
      .update(payload, 'utf8')
      .digest('hex');
    
    const receivedSignature = signature.replace('sha256=', '');
    
    return crypto.timingSafeEqual(
      Buffer.from(expectedSignature, 'hex'),
      Buffer.from(receivedSignature, 'hex')
    );
  } catch (error) {
    console.error('❌ Signature verification error:', error.message);
    return false;
  }
}

// Main webhook endpoint
app.post('/webhook', (req, res) => {
  const startTime = Date.now();
  webhookCount++;
  
  const headers = {
    signature: req.headers['x-webhook-signature-256'],
    event: req.headers['x-webhook-event'],
    deliveryId: req.headers['x-webhook-delivery'],
    userAgent: req.headers['user-agent']
  };
  
  const payload = req.body;
  
  console.log('\n' + '='.repeat(60));
  console.log(`🔔 Webhook #${webhookCount} Received at ${new Date().toISOString()}`);
  console.log('='.repeat(60));
  console.log('📧 Event:', headers.event || 'UNKNOWN');
  console.log('🆔 Delivery ID:', headers.deliveryId || 'NONE');
  console.log('🤖 User Agent:', headers.userAgent || 'NONE');
  console.log('🔐 Signature:', headers.signature || 'NONE');
  console.log('📏 Payload Size:', payload.length, 'bytes');
  
  // Get secret from environment or use default for testing
  const secret = process.env.WEBHOOK_SECRET || 'default-test-secret';
  
  // Verify signature
  const isValidSignature = verifySignature(payload, headers.signature, secret);
  
  if (isValidSignature) {
    console.log('✅ Signature verification: PASSED');
    
    try {
      const parsedPayload = JSON.parse(payload);
      console.log('📦 Parsed Payload:');
      console.log(JSON.stringify(parsedPayload, null, 2));
      
      // Store webhook for analysis
      const webhookRecord = {
        id: webhookCount,
        timestamp: new Date().toISOString(),
        headers,
        payload: parsedPayload,
        processingTime: Date.now() - startTime,
        valid: true
      };
      
      receivedWebhooks.unshift(webhookRecord); // Add to beginning
      
      // Keep only last 100 webhooks
      if (receivedWebhooks.length > 100) {
        receivedWebhooks.splice(100);
      }
      
      console.log('✅ Webhook processed successfully');
      res.status(200).send('OK');
      
    } catch (parseError) {
      console.log('❌ JSON parsing failed:', parseError.message);
      console.log('📄 Raw payload:', payload.toString());
      res.status(400).send('Invalid JSON payload');
    }
    
  } else {
    console.log('❌ Signature verification: FAILED');
    console.log('🔑 Expected secret length:', secret.length);
    console.log('📝 Raw payload for debugging:', payload.toString().substring(0, 200) + '...');
    
    // Store invalid webhook for debugging
    const webhookRecord = {
      id: webhookCount,
      timestamp: new Date().toISOString(),
      headers,
      payload: payload.toString(),
      processingTime: Date.now() - startTime,
      valid: false,
      error: 'Invalid signature'
    };
    
    receivedWebhooks.unshift(webhookRecord);
    
    res.status(401).send('Invalid signature');
  }
  
  console.log('⏱️  Processing time:', Date.now() - startTime, 'ms');
  console.log('='.repeat(60));
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    webhooksReceived: webhookCount,
    uptime: process.uptime()
  });
});

// Stats endpoint
app.get('/stats', (req, res) => {
  const validWebhooks = receivedWebhooks.filter(w => w.valid).length;
  const invalidWebhooks = receivedWebhooks.filter(w => !w.valid).length;
  
  res.json({
    total: webhookCount,
    stored: receivedWebhooks.length,
    valid: validWebhooks,
    invalid: invalidWebhooks,
    uptime: process.uptime(),
    lastWebhook: receivedWebhooks[0] || null
  });
});

// List recent webhooks
app.get('/webhooks', (req, res) => {
  const limit = parseInt(req.query.limit) || 10;
  const recent = receivedWebhooks.slice(0, limit);
  
  res.json({
    count: recent.length,
    total: webhookCount,
    webhooks: recent
  });
});

// Clear webhook history
app.delete('/webhooks', (req, res) => {
  const cleared = receivedWebhooks.length;
  receivedWebhooks.splice(0);
  webhookCount = 0;
  
  res.json({
    message: 'Webhook history cleared',
    cleared: cleared
  });
});

// Test endpoint that always returns success
app.post('/webhook/test-success', (req, res) => {
  console.log('🧪 Test endpoint hit - returning success');
  res.status(200).send('Test OK');
});

// Test endpoint that always returns error
app.post('/webhook/test-error', (req, res) => {
  console.log('🧪 Test endpoint hit - returning error');
  res.status(500).send('Test Error');
});

// Test endpoint with random success/failure
app.post('/webhook/test-random', (req, res) => {
  const success = Math.random() > 0.5;
  console.log(`🧪 Test endpoint hit - returning ${success ? 'success' : 'error'}`);
  
  if (success) {
    res.status(200).send('Random Success');
  } else {
    res.status(500).send('Random Error');
  }
});

// Simple web interface
app.get('/', (req, res) => {
  const secret = process.env.WEBHOOK_SECRET || 'default-test-secret';
  const html = `
<!DOCTYPE html>
<html>
<head>
    <title>Webhook Test Server - Screenshot API</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; }
        .header { text-align: center; color: #333; margin-bottom: 30px; }
        .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 30px; }
        .stat-card { background: #e3f2fd; padding: 15px; border-radius: 8px; text-align: center; }
        .stat-number { font-size: 2em; font-weight: bold; color: #1976d2; }
        .stat-label { color: #666; }
        .webhook-list { max-height: 400px; overflow-y: auto; border: 1px solid #ddd; border-radius: 4px; }
        .webhook-item { padding: 10px; border-bottom: 1px solid #eee; font-family: monospace; }
        .webhook-item.invalid { background-color: #ffebee; }
        .webhook-item.valid { background-color: #e8f5e8; }
        .controls { margin: 20px 0; }
        button { background: #1976d2; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; margin-right: 10px; }
        button:hover { background: #1565c0; }
        .endpoint-info { background: #f9f9f9; padding: 15px; border-radius: 4px; margin: 15px 0; }
        code { background: #e0e0e0; padding: 2px 6px; border-radius: 2px; }
        .warning { background: #fff3cd; border: 1px solid #ffeaa7; padding: 10px; border-radius: 4px; margin: 15px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🎯 Webhook Test Server</h1>
            <p>Screenshot API Webhook Testing & Development</p>
            <p>Listening on port ${PORT}</p>
        </div>
        
        <div class="warning">
            <strong>⚠️ Development Example Only:</strong> This is for testing and development. 
            Do not use in production without proper security measures.
        </div>
        
        <div class="stats" id="stats">
            <div class="stat-card">
                <div class="stat-number" id="total-count">0</div>
                <div class="stat-label">Total Webhooks</div>
            </div>
            <div class="stat-card">
                <div class="stat-number" id="valid-count">0</div>
                <div class="stat-label">Valid Signatures</div>
            </div>
            <div class="stat-card">
                <div class="stat-number" id="invalid-count">0</div>
                <div class="stat-label">Invalid Signatures</div>
            </div>
            <div class="stat-card">
                <div class="stat-number" id="uptime">0s</div>
                <div class="stat-label">Uptime</div>
            </div>
        </div>

        <div class="endpoint-info">
            <h3>📡 Available Endpoints:</h3>
            <ul>
                <li><code>POST /webhook</code> - Main webhook receiver with HMAC validation</li>
                <li><code>POST /webhook/test-success</code> - Always returns 200 (success testing)</li>
                <li><code>POST /webhook/test-error</code> - Always returns 500 (retry testing)</li>
                <li><code>POST /webhook/test-random</code> - Random success/error (reliability testing)</li>
                <li><code>GET /stats</code> - JSON statistics for monitoring</li>
                <li><code>GET /webhooks</code> - Recent webhooks history (JSON)</li>
                <li><code>GET /health</code> - Health check endpoint</li>
                <li><code>DELETE /webhooks</code> - Clear webhook history</li>
            </ul>
            <p><strong>Environment:</strong> WEBHOOK_SECRET = ${secret.substring(0, 8)}...</p>
        </div>
        
        <div class="controls">
            <button onclick="loadStats()">🔄 Refresh</button>
            <button onclick="clearWebhooks()">🗑️ Clear History</button>
            <button onclick="loadWebhooks()">📋 Load Recent</button>
        </div>
        
        <div class="webhook-list" id="webhook-list">
            <div style="padding: 20px; text-align: center; color: #666;">
                No webhooks received yet. Configure a webhook in the Screenshot API to start receiving events.
            </div>
        </div>
    </div>

    <script>
        async function loadStats() {
            try {
                const response = await fetch('/stats');
                const stats = await response.json();
                
                document.getElementById('total-count').textContent = stats.total;
                document.getElementById('valid-count').textContent = stats.valid;
                document.getElementById('invalid-count').textContent = stats.invalid;
                document.getElementById('uptime').textContent = Math.floor(stats.uptime) + 's';
            } catch (error) {
                console.error('Error loading stats:', error);
            }
        }
        
        async function loadWebhooks() {
            try {
                const response = await fetch('/webhooks?limit=20');
                const data = await response.json();
                
                const container = document.getElementById('webhook-list');
                
                if (data.webhooks.length === 0) {
                    container.innerHTML = '<div style="padding: 20px; text-align: center; color: #666;">No webhooks received yet.</div>';
                    return;
                }
                
                container.innerHTML = data.webhooks.map(webhook => 
                    '<div class="webhook-item ' + (webhook.valid ? 'valid' : 'invalid') + '">' +
                    '<strong>' + (webhook.headers.event || 'UNKNOWN') + '</strong> - ' +
                    webhook.timestamp + ' (' + webhook.processingTime + 'ms)<br>' +
                    'ID: ' + (webhook.headers.deliveryId || 'N/A') + '<br>' +
                    'Valid: ' + (webhook.valid ? '✅' : '❌') +
                    (webhook.error ? '<br>Error: ' + webhook.error : '') +
                    '</div>'
                ).join('');
            } catch (error) {
                console.error('Error loading webhooks:', error);
            }
        }
        
        async function clearWebhooks() {
            if (confirm('Clear all webhook history?')) {
                try {
                    await fetch('/webhooks', { method: 'DELETE' });
                    loadStats();
                    loadWebhooks();
                } catch (error) {
                    console.error('Error clearing webhooks:', error);
                }
            }
        }
        
        // Auto-refresh every 5 seconds
        setInterval(() => {
            loadStats();
            loadWebhooks();
        }, 5000);
        
        // Initial load
        loadStats();
        loadWebhooks();
    </script>
</body>
</html>`;
  
  res.send(html);
});

// Start server
app.listen(PORT, () => {
  console.log('\n' + '🎯'.repeat(20));
  console.log('🚀 Webhook Test Server Started');
  console.log('🎯'.repeat(20));
  console.log(`📡 Listening on: http://localhost:${PORT}`);
  console.log(`🌐 Web Interface: http://localhost:${PORT}`);
  console.log(`📊 Stats API: http://localhost:${PORT}/stats`);
  console.log(`🔍 Health Check: http://localhost:${PORT}/health`);
  console.log('🎯'.repeat(20));
  console.log(`🔑 Using secret: ${(process.env.WEBHOOK_SECRET || 'default-test-secret').substring(0, 8)}...`);
  console.log('💡 Set WEBHOOK_SECRET environment variable for custom secret');
  console.log('⚠️  This is a development example - not for production use');
  console.log('\n✅ Ready to receive webhooks!\n');
});

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n👋 Shutting down webhook test server...');
  console.log(`📊 Final stats: ${webhookCount} webhooks received`);
  process.exit(0);
});