# Webhook Implementation Guide

## Overview

The Screenshot API provides a comprehensive webhook system that allows you to receive real-time notifications about events in your account. This implementation follows Clean Architecture principles with enterprise-level security, reliability, and observability features.

## Architecture

### Components

```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   REST Controller   │    │    Use Cases        │    │   Domain Entities   │
│                     │    │                     │    │                     │
│ • Authentication    │────│ • CreateWebhook     │────│ • WebhookConfig     │
│ • Request/Response  │    │ • UpdateWebhook     │    │ • WebhookDelivery   │
│ • Error Handling    │    │ • SendWebhook       │    │ • WebhookEvent      │
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘
           │                           │                           │
           │                           │                           │
           ▼                           ▼                           ▼
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   Infrastructure    │    │   Repositories      │    │    Database         │
│                     │    │                     │    │                     │
│ • HTTP Client       │    │ • PostgreSQL        │    │ • webhook_configs   │
│ • HMAC Signing      │    │ • In-Memory (Dev)   │    │ • webhook_deliveries│
│ • Retry Logic       │    │ • Caching Layer     │    │ • Indexes & FKs     │
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘
```

### Key Principles

- **Clean Architecture**: Clear separation between domain, use cases, and infrastructure
- **Security First**: HMAC-SHA256 signing, ownership validation, secure secrets
- **Reliability**: Exponential backoff retry with 5 attempts
- **Observability**: Complete delivery tracking and analytics
- **Type Safety**: Proper DTOs and domain entities

## Authentication & Security

### API Authentication (Managing Webhooks)

Three authentication methods are supported for webhook management:

#### 1. JWT Bearer Token
```http
POST /api/v1/webhooks
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "url": "https://your-app.com/webhooks",
  "events": ["SCREENSHOT_COMPLETED", "CREDITS_LOW"],
  "description": "Production webhook"
}
```

#### 2. API Key
```http
POST /api/v1/webhooks
X-API-Key: sk_live_1234567890abcdef...
Content-Type: application/json

{
  "url": "https://your-app.com/webhooks",
  "events": ["SCREENSHOT_COMPLETED"]
}
```

#### 3. API Key ID + JWT (Recommended for Web Apps)
```http
POST /api/v1/webhooks
X-API-Key-ID: ak_1234567890
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

### Webhook Authentication (Receiving Webhooks)

All outgoing webhooks use **HMAC-SHA256** signatures for authentication:

```http
POST https://your-app.com/webhooks
Content-Type: application/json
X-Webhook-Event: SCREENSHOT_COMPLETED
X-Webhook-Signature-256: sha256=8b5f48702995c1598c573db1e21866a9b825d4a794d169d7060a03605796360b
X-Webhook-Delivery: del_1234567890abcdef
User-Agent: ScreenshotAPI-Webhook/1.0

{
  "event": "SCREENSHOT_COMPLETED",
  "timestamp": "2023-12-01T10:30:00Z",
  "data": {
    "screenshotId": "job_abc123",
    "userId": "user_456",
    "status": "COMPLETED",
    "resultUrl": "https://storage.example.com/screenshots/abc123.png"
  }
}
```

#### HMAC Validation (Your Server)

##### JavaScript/Node.js
```javascript
const crypto = require('crypto');

function validateWebhook(payload, signature, secret) {
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

// Express.js example
app.post('/webhooks', express.raw({type: 'application/json'}), (req, res) => {
  const signature = req.headers['x-webhook-signature-256'];
  const payload = req.body;
  
  if (!validateWebhook(payload, signature, process.env.WEBHOOK_SECRET)) {
    return res.status(401).send('Invalid signature');
  }
  
  // Process webhook
  const event = JSON.parse(payload);
  console.log('Received event:', event.event);
  
  res.status(200).send('OK');
});
```

##### Java/Spring Boot
```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;

@RestController
public class WebhookController {
    
    @Value("${webhook.secret}")
    private String webhookSecret;
    
    @PostMapping("/webhooks")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Webhook-Signature-256") String signature) {
        
        if (!validateWebhook(payload, signature, webhookSecret)) {
            return ResponseEntity.status(401).body("Invalid signature");
        }
        
        // Process webhook
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode event = mapper.readTree(payload);
            System.out.println("Received event: " + event.get("event").asText());
            
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Processing error");
        }
    }
    
    private boolean validateWebhook(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] expectedSignature = mac.doFinal(payload.getBytes());
            String expectedHex = bytesToHex(expectedSignature);
            
            String receivedSignature = signature.replace("sha256=", "");
            
            return MessageDigest.isEqual(
                expectedHex.getBytes(),
                receivedSignature.getBytes()
            );
        } catch (Exception e) {
            return false;
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
```

##### Kotlin/Ktor
```kotlin
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

fun Route.webhookRoutes() {
    val webhookSecret = System.getenv("WEBHOOK_SECRET")
    val json = Json { ignoreUnknownKeys = true }
    
    post("/webhooks") {
        val payload = call.receiveText()
        val signature = call.request.headers["X-Webhook-Signature-256"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing signature")
        
        if (!validateWebhook(payload, signature, webhookSecret)) {
            return@post call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
        }
        
        // Process webhook
        try {
            val event = json.parseToJsonElement(payload).jsonObject
            val eventType = event["event"]?.jsonPrimitive?.content
            println("Received event: $eventType")
            
            call.respond(HttpStatusCode.OK, "OK")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Processing error")
        }
    }
}

fun validateWebhook(payload: String, signature: String, secret: String): Boolean {
    return try {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        
        val expectedSignature = mac.doFinal(payload.toByteArray())
        val expectedHex = expectedSignature.joinToString("") { "%02x".format(it) }
        
        val receivedSignature = signature.removePrefix("sha256=")
        
        MessageDigest.isEqual(
            expectedHex.toByteArray(),
            receivedSignature.toByteArray()
        )
    } catch (e: Exception) {
        false
    }
}
```

##### Python
```python
import hmac
import hashlib
import json

def verify_webhook_signature(payload: bytes, signature: str, secret: str) -> bool:
    expected_signature = hmac.new(
        secret.encode('utf-8'),
        payload,
        hashlib.sha256
    ).hexdigest()
    
    received_signature = signature.replace('sha256=', '')
    
    return hmac.compare_digest(expected_signature, received_signature)

# Flask example
from flask import Flask, request

@app.route('/webhook', methods=['POST'])
def webhook():
    signature = request.headers.get('X-Webhook-Signature-256')
    payload = request.get_data()
    secret = os.environ['WEBHOOK_SECRET']
    
    if not verify_webhook_signature(payload, signature, secret):
        return 'Invalid signature', 401
    
    event = request.get_json()
    print(f"Webhook received: {event}")
    
    return 'OK', 200
```

##### PHP
```php
function verifyWebhookSignature($payload, $signature, $secret) {
    $expectedSignature = hash_hmac('sha256', $payload, $secret);
    $receivedSignature = str_replace('sha256=', '', $signature);
    
    return hash_equals($expectedSignature, $receivedSignature);
}

// Usage
$payload = file_get_contents('php://input');
$signature = $_SERVER['HTTP_X_WEBHOOK_SIGNATURE_256'];
$secret = $_ENV['WEBHOOK_SECRET'];

if (!verifyWebhookSignature($payload, $signature, $secret)) {
    http_response_code(401);
    exit('Invalid signature');
}

$event = json_decode($payload, true);
echo "Webhook received: " . print_r($event, true);
```

## Events & Payloads

### Available Events

| Event | Description | Trigger |
|-------|-------------|---------|
| `SCREENSHOT_COMPLETED` | Screenshot generated successfully | Job completes |
| `SCREENSHOT_FAILED` | Screenshot generation failed | Job fails permanently |
| `CREDITS_LOW` | Credits below 20% | Usage monitoring |
| `CREDITS_EXHAUSTED` | Credits reached 0 | Usage monitoring |
| `SUBSCRIPTION_RENEWED` | Subscription renewed | Billing cycle |
| `SUBSCRIPTION_CANCELLED` | Subscription cancelled | User action |
| `PAYMENT_SUCCESSFUL` | Payment processed | Billing |
| `PAYMENT_FAILED` | Payment failed | Billing |
| `PAYMENT_PROCESSED` | Payment successful (alias) | Billing |
| `USER_REGISTERED` | New user registered | Registration |
| `WEBHOOK_TEST` | Webhook test event | Testing |

### Payload Structure

All webhook payloads follow this structure:

```json
{
  "event": "EVENT_NAME",
  "timestamp": "2023-12-01T10:30:00.123Z",
  "data": {
    // Event-specific data
  }
}
```

### Event-Specific Data

#### SCREENSHOT_COMPLETED
```json
{
  "event": "SCREENSHOT_COMPLETED",
  "timestamp": "2023-12-01T10:30:00Z",
  "data": {
    "screenshotId": "job_abc123",
    "userId": "user_456",
    "status": "COMPLETED",
    "resultUrl": "https://storage.example.com/screenshots/abc123.png",
    "requestedAt": "2023-12-01T10:29:45Z",
    "completedAt": "2023-12-01T10:30:00Z",
    "processingTimeMs": 15000,
    "metadata": {
      "url": "https://example.com",
      "format": "PNG",
      "width": 1920,
      "height": 1080
    }
  }
}
```

#### CREDITS_LOW
```json
{
  "event": "CREDITS_LOW",
  "timestamp": "2023-12-01T10:30:00Z",
  "data": {
    "userId": "user_456",
    "currentCredits": 45,
    "totalCredits": 300,
    "percentage": 15.0,
    "threshold": 20.0,
    "planName": "Starter"
  }
}
```

#### PAYMENT_SUCCESSFUL
```json
{
  "event": "PAYMENT_SUCCESSFUL",
  "timestamp": "2023-12-01T10:30:00Z",
  "data": {
    "userId": "user_456",
    "paymentId": "pay_123456",
    "amount": 2999,
    "currency": "USD",
    "planName": "Pro",
    "creditsAdded": 10000,
    "billingPeriod": "monthly"
  }
}
```

## Webhook Management API

### Create Webhook

#### HTTP Request
```http
POST /api/v1/webhooks
Authorization: Bearer <token>
Content-Type: application/json

{
  "url": "https://your-app.com/webhooks",
  "events": ["SCREENSHOT_COMPLETED", "CREDITS_LOW"],
  "description": "Production webhook for notifications"
}
```

#### Client Examples

##### JavaScript/Fetch
```javascript
async function createWebhook(token, webhookConfig) {
  const response = await fetch('https://api.screenshot.dev/api/v1/webhooks', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      url: webhookConfig.url,
      events: webhookConfig.events,
      description: webhookConfig.description
    })
  });

  if (!response.ok) {
    throw new Error(`Failed to create webhook: ${response.statusText}`);
  }

  return await response.json();
}

// Usage
const webhook = await createWebhook(userToken, {
  url: 'https://your-app.com/webhooks',
  events: ['SCREENSHOT_COMPLETED', 'CREDITS_LOW'],
  description: 'Production webhook'
});

console.log('Webhook created:', webhook.id);
console.log('Secret:', webhook.secret); // Store this securely!
```

##### Java/OkHttp
```java
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WebhookManager {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl = "https://api.screenshot.dev";
    
    public WebhookResponse createWebhook(String token, WebhookRequest request) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", request.getUrl());
        payload.put("events", request.getEvents());
        payload.put("description", request.getDescription());
        
        RequestBody body = RequestBody.create(
            mapper.writeValueAsString(payload),
            MediaType.get("application/json")
        );
        
        Request httpRequest = new Request.Builder()
            .url(baseUrl + "/api/v1/webhooks")
            .post(body)
            .addHeader("Authorization", "Bearer " + token)
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to create webhook: " + response.message());
            }
            
            return mapper.readValue(response.body().string(), WebhookResponse.class);
        }
    }
}

// Usage
WebhookRequest request = new WebhookRequest(
    "https://your-app.com/webhooks",
    Arrays.asList("SCREENSHOT_COMPLETED", "CREDITS_LOW"),
    "Production webhook"
);

WebhookResponse webhook = webhookManager.createWebhook(userToken, request);
System.out.println("Webhook created: " + webhook.getId());
System.out.println("Secret: " + webhook.getSecret()); // Store securely!
```

##### Kotlin/Ktor Client
```kotlin
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CreateWebhookRequest(
    val url: String,
    val events: List<String>,
    val description: String
)

@Serializable
data class WebhookResponse(
    val id: String,
    val userId: String,
    val url: String,
    val events: List<String>,
    val isActive: Boolean,
    val description: String,
    val secret: String,
    val createdAt: String,
    val updatedAt: String
)

class WebhookManager(private val client: HttpClient) {
    private val baseUrl = "https://api.screenshot.dev"
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun createWebhook(token: String, request: CreateWebhookRequest): WebhookResponse {
        val response = client.post("$baseUrl/api/v1/webhooks") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody(request)
        }
        
        if (!response.status.isSuccess()) {
            error("Failed to create webhook: ${response.status}")
        }
        
        return json.decodeFromString<WebhookResponse>(response.bodyAsText())
    }
}

// Usage
val request = CreateWebhookRequest(
    url = "https://your-app.com/webhooks",
    events = listOf("SCREENSHOT_COMPLETED", "CREDITS_LOW"),
    description = "Production webhook"
)

val webhook = webhookManager.createWebhook(userToken, request)
println("Webhook created: ${webhook.id}")
println("Secret: ${webhook.secret}") // Store securely!
```

**Response:**
```json
{
  "id": "wh_1234567890abcdef",
  "userId": "user_456",
  "url": "https://your-app.com/webhooks",
  "events": ["SCREENSHOT_COMPLETED", "CREDITS_LOW"],
  "isActive": true,
  "description": "Production webhook for notifications",
  "secret": "whs_1234567890abcdef1234567890abcdef",
  "createdAt": "2023-12-01T10:30:00Z",
  "updatedAt": "2023-12-01T10:30:00Z"
}
```

### List Webhooks

```http
GET /api/v1/webhooks
Authorization: Bearer <token>
```

**Response:**
```json
{
  "webhooks": [
    {
      "id": "wh_1234567890abcdef",
      "userId": "user_456",
      "url": "https://your-app.com/webhooks",
      "events": ["SCREENSHOT_COMPLETED", "CREDITS_LOW"],
      "isActive": true,
      "description": "Production webhook",
      "createdAt": "2023-12-01T10:30:00Z",
      "updatedAt": "2023-12-01T10:30:00Z"
    }
  ],
  "total": 1
}
```

### Update Webhook

```http
PUT /api/v1/webhooks/{webhookId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "events": ["SCREENSHOT_COMPLETED", "SCREENSHOT_FAILED"],
  "isActive": false
}
```

### Delete Webhook

```http
DELETE /api/v1/webhooks/{webhookId}
Authorization: Bearer <token>
```

### Regenerate Secret

```http
POST /api/v1/webhooks/{webhookId}/regenerate-secret
Authorization: Bearer <token>
```

### Test Webhook

```http
POST /api/v1/webhooks/{webhookId}/test
Authorization: Bearer <token>
```

## Delivery & Reliability

### Retry Logic

Webhook deliveries use exponential backoff with the following schedule:

- **Attempt 1**: Immediate
- **Attempt 2**: 1 minute delay
- **Attempt 3**: 5 minutes delay
- **Attempt 4**: 15 minutes delay  
- **Attempt 5**: 30 minutes delay
- **Final Attempt**: 60 minutes delay

### Success Criteria

A webhook delivery is considered successful if:
- HTTP status code is 2xx (200-299)
- Response received within 30 seconds
- No network errors occur

### Failure Handling

Webhooks are retried if:
- HTTP status code is 5xx (server errors)
- HTTP status code is 429 (rate limited)
- Network timeout or connection error
- DNS resolution failure

Webhooks are **not retried** for:
- HTTP status code 4xx (except 429)
- Invalid webhook URL
- Maximum attempts exceeded (5 attempts)

### Delivery Status

| Status | Description |
|--------|-------------|
| `PENDING` | Queued for delivery |
| `DELIVERING` | Currently being sent |
| `DELIVERED` | Successfully delivered |
| `RETRYING` | Failed but will retry |
| `FAILED` | Permanently failed |

## Analytics & Monitoring

### Delivery History

```http
GET /api/v1/webhooks/{webhookId}/deliveries?limit=50
Authorization: Bearer <token>
```

### Delivery Statistics

```http
GET /api/v1/webhooks/{webhookId}/stats?days=30
Authorization: Bearer <token>
```

**Response:**
```json
{
  "total": 1250,
  "delivered": 1198,
  "failed": 52,
  "pending": 0,
  "successRate": 0.9584,
  "averageResponseTimeMs": 245
}
```

### Global Delivery History

```http
GET /api/v1/webhooks/deliveries?limit=100
Authorization: Bearer <token>
```

## Error Handling

### Common Error Responses

#### 400 Bad Request
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid webhook URL format",
  "details": {
    "field": "url",
    "code": "INVALID_FORMAT"
  }
}
```

#### 403 Forbidden
```json
{
  "error": "FORBIDDEN",
  "message": "Webhook limit exceeded (max 10 per user)"
}
```

#### 404 Not Found
```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Webhook not found",
  "resourceType": "Webhook",
  "resourceId": "wh_1234567890abcdef"
}
```

### Webhook Endpoint Requirements

Your webhook endpoint should:

1. **Respond quickly** (< 30 seconds)
2. **Return 2xx status** for successful processing
3. **Validate HMAC signature** before processing
4. **Handle duplicate deliveries** (use delivery ID for deduplication)
5. **Log webhook events** for debugging

### Example Webhook Handlers

#### JavaScript/Node.js
```javascript
const express = require('express');
const crypto = require('crypto');
const app = express();

// Middleware to capture raw body for signature validation
app.use('/webhooks', express.raw({type: 'application/json'}));

app.post('/webhooks', (req, res) => {
  const signature = req.headers['x-webhook-signature-256'];
  const deliveryId = req.headers['x-webhook-delivery'];
  const event = req.headers['x-webhook-event'];
  
  // Validate signature
  if (!validateWebhook(req.body, signature, process.env.WEBHOOK_SECRET)) {
    console.error('Invalid webhook signature');
    return res.status(401).send('Unauthorized');
  }
  
  // Check for duplicate delivery
  if (isDeliveryProcessed(deliveryId)) {
    console.log('Duplicate delivery:', deliveryId);
    return res.status(200).send('Already processed');
  }
  
  // Process webhook
  try {
    const payload = JSON.parse(req.body);
    processWebhookEvent(payload);
    
    // Mark as processed
    markDeliveryProcessed(deliveryId);
    
    res.status(200).send('OK');
  } catch (error) {
    console.error('Webhook processing error:', error);
    res.status(500).send('Processing error');
  }
});

function processWebhookEvent(payload) {
  switch (payload.event) {
    case 'SCREENSHOT_COMPLETED':
      handleScreenshotCompleted(payload.data);
      break;
    case 'CREDITS_LOW':
      handleCreditsLow(payload.data);
      break;
    default:
      console.log('Unknown event:', payload.event);
  }
}

app.listen(3000, () => {
  console.log('Webhook server running on port 3000');
});
```

#### Java/Spring Boot
```java
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class WebhookHandler {
    
    private final Set<String> processedDeliveries = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostMapping("/webhooks")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Webhook-Signature-256") String signature,
            @RequestHeader("X-Webhook-Delivery") String deliveryId,
            @RequestHeader("X-Webhook-Event") String event) {
        
        // Validate signature
        if (!validateWebhook(payload, signature, System.getenv("WEBHOOK_SECRET"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        
        // Check for duplicate delivery
        if (processedDeliveries.contains(deliveryId)) {
            return ResponseEntity.ok("Already processed");
        }
        
        try {
            JsonNode webhookData = objectMapper.readTree(payload);
            processWebhookEvent(webhookData);
            
            // Mark as processed
            processedDeliveries.add(deliveryId);
            
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }
    }
    
    private void processWebhookEvent(JsonNode payload) {
        String eventType = payload.get("event").asText();
        JsonNode data = payload.get("data");
        
        switch (eventType) {
            case "SCREENSHOT_COMPLETED":
                handleScreenshotCompleted(data);
                break;
            case "CREDITS_LOW":
                handleCreditsLow(data);
                break;
            default:
                System.out.println("Unknown event: " + eventType);
        }
    }
    
    private void handleScreenshotCompleted(JsonNode data) {
        String jobId = data.get("jobId").asText();
        String resultUrl = data.get("resultUrl").asText();
        
        // Process screenshot completion
        System.out.println("Screenshot completed: " + jobId + " -> " + resultUrl);
    }
    
    private void handleCreditsLow(JsonNode data) {
        String userId = data.get("userId").asText();
        int currentCredits = data.get("currentCredits").asInt();
        
        // Send notification to user
        System.out.println("Credits low for user " + userId + ": " + currentCredits);
    }
}
```

#### Kotlin/Ktor
```kotlin
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

fun Route.webhookHandler() {
    val processedDeliveries = ConcurrentHashMap.newKeySet<String>()
    val json = Json { ignoreUnknownKeys = true }
    
    post("/webhooks") {
        val payload = call.receiveText()
        val signature = call.request.headers["X-Webhook-Signature-256"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing signature")
        val deliveryId = call.request.headers["X-Webhook-Delivery"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing delivery ID")
        val event = call.request.headers["X-Webhook-Event"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing event")
        
        // Validate signature
        if (!validateWebhook(payload, signature, System.getenv("WEBHOOK_SECRET"))) {
            return@post call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
        }
        
        // Check for duplicate delivery
        if (processedDeliveries.contains(deliveryId)) {
            return@post call.respond(HttpStatusCode.OK, "Already processed")
        }
        
        try {
            val webhookData = json.parseToJsonElement(payload).jsonObject
            processWebhookEvent(webhookData)
            
            // Mark as processed
            processedDeliveries.add(deliveryId)
            
            call.respond(HttpStatusCode.OK, "OK")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Processing error")
        }
    }
}

fun processWebhookEvent(payload: JsonObject) {
    val eventType = payload["event"]?.jsonPrimitive?.content ?: return
    val data = payload["data"]?.jsonObject ?: return
    
    when (eventType) {
        "SCREENSHOT_COMPLETED" -> handleScreenshotCompleted(data)
        "CREDITS_LOW" -> handleCreditsLow(data)
        else -> println("Unknown event: $eventType")
    }
}

fun handleScreenshotCompleted(data: JsonObject) {
    val jobId = data["jobId"]?.jsonPrimitive?.content
    val resultUrl = data["resultUrl"]?.jsonPrimitive?.content
    
    // Process screenshot completion
    println("Screenshot completed: $jobId -> $resultUrl")
}

fun handleCreditsLow(data: JsonObject) {
    val userId = data["userId"]?.jsonPrimitive?.content
    val currentCredits = data["currentCredits"]?.jsonPrimitive?.int
    
    // Send notification to user
    println("Credits low for user $userId: $currentCredits")
}
```

## Security Best Practices

### For Your Webhook Endpoints

1. **Always validate HMAC signatures**
2. **Use HTTPS endpoints only**
3. **Implement rate limiting**
4. **Log all webhook events**
5. **Handle duplicate deliveries**
6. **Use timing-safe comparison for signatures**
7. **Validate event data before processing**

### For Screenshot API Integration

1. **Store webhook secrets securely**
2. **Rotate secrets regularly**
3. **Use dedicated webhook endpoints**
4. **Monitor delivery success rates**
5. **Implement proper error handling**
6. **Set up alerting for failed deliveries**

## Troubleshooting

### Common Issues

#### Webhooks Not Received
- Verify webhook URL is accessible
- Check HTTPS certificate validity
- Ensure endpoint responds within 30 seconds
- Verify firewall/network configuration

#### Signature Validation Failures
- Ensure using correct webhook secret
- Verify HMAC calculation implementation
- Check for any payload modification
- Use raw request body for signature validation

#### High Failure Rates
- Check endpoint response times
- Monitor server error rates
- Verify network connectivity
- Review webhook endpoint logs

### Debug Endpoints

```http
GET /api/v1/webhooks/debug/stats
Authorization: Bearer <token>
```

This endpoint provides comprehensive debugging information including:
- Total webhook configurations
- Active webhook count
- Delivery statistics breakdown
- Available events list
- Endpoint documentation

## Rate Limits

- **Webhook configurations**: 10 per user
- **Webhook URL length**: 2048 characters maximum
- **Delivery timeout**: 30 seconds
- **Retry attempts**: 5 maximum
- **Secret length**: 32 characters minimum

## Webhook Events Integration

The webhook system integrates with the following Screenshot API events:

### Screenshot Lifecycle
- Triggered when jobs complete or fail
- Includes full job metadata and results
- Provides processing time and error details

### Credit Management
- Monitors credit usage automatically
- Configurable thresholds (default: 20% for low warning)
- Real-time balance updates

### Billing Integration
- Stripe payment event forwarding
- Subscription lifecycle notifications
- Automatic credit allocation tracking

This comprehensive webhook system provides enterprise-grade reliability and security for real-time event notifications in your Screenshot API integration.