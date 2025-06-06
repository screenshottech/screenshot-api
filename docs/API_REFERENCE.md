# 📡 API Reference

## Overview

The Screenshot API provides a robust, production-ready service for generating high-quality screenshots and PDFs from web pages. Built with performance and reliability in mind, it handles everything from simple captures to complex automated workflows.

## 🔐 Authentication

All API endpoints require authentication using either API Keys or JWT tokens.

### API Key Authentication
```bash
Authorization: Bearer sk_your_api_key_here
```

### JWT Authentication
```bash
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## 📋 Base URL

```
Production: https://your-domain.com/api/v1
Development: http://localhost:8080/api/v1
```

## 🖼️ Screenshots

### Create Screenshot

Generate a screenshot from any publicly accessible URL.

**Endpoint:** `POST /screenshot`

**Request Headers:**
```http
Authorization: Bearer sk_your_api_key
Content-Type: application/json
```

**Request Body:**
```json
{
  "url": "https://example.com",
  "format": "PNG",
  "width": 1920,
  "height": 1080,
  "fullPage": true,
  "waitTime": 2000,
  "webhookUrl": "https://your-app.com/webhook/screenshot-complete"
}
```

**Parameters:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `url` | string | ✅ | - | Target URL to capture |
| `format` | string | ❌ | `PNG` | Output format: `PNG`, `JPEG`, `PDF` |
| `width` | integer | ❌ | `1200` | Viewport width (100-1920) |
| `height` | integer | ❌ | `800` | Viewport height (100-1080) |
| `fullPage` | boolean | ❌ | `false` | Capture entire page height |
| `waitTime` | integer | ❌ | `1000` | Wait time before capture (ms) |
| `webhookUrl` | string | ❌ | - | Callback URL for completion notification |

**Response:**
```json
{
  "jobId": "job_abc123def456",
  "status": "QUEUED",
  "estimatedTime": 5000,
  "webhookUrl": "https://your-app.com/webhook/screenshot-complete",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Status Codes:**
- `201` - Screenshot job created successfully
- `400` - Invalid request parameters
- `401` - Authentication required
- `403` - Insufficient credits or permissions
- `429` - Rate limit exceeded

### Get Screenshot Status

Check the progress and retrieve results of a screenshot job.

**Endpoint:** `GET /screenshot/{jobId}`

**Response:**
```json
{
  "jobId": "job_abc123def456",
  "status": "COMPLETED",
  "url": "https://storage.example.com/screenshots/abc123.png",
  "metadata": {
    "format": "PNG",
    "fileSize": 245760,
    "dimensions": {
      "width": 1920,
      "height": 1080
    }
  },
  "processingTime": 3200,
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:30:03Z"
}
```

**Job Status Values:**
- `QUEUED` - Job is waiting to be processed
- `PROCESSING` - Screenshot is being generated
- `COMPLETED` - Screenshot ready for download
- `FAILED` - Job failed (see error message)

### List Screenshots

Retrieve your screenshot history with filtering and pagination.

**Endpoint:** `GET /screenshots`

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | `1` | Page number |
| `limit` | integer | `50` | Items per page (max 100) |
| `status` | string | - | Filter by status |
| `format` | string | - | Filter by format |

**Response:**
```json
{
  "screenshots": [
    {
      "jobId": "job_abc123def456",
      "status": "COMPLETED",
      "url": "https://storage.example.com/screenshots/abc123.png",
      "originalUrl": "https://example.com",
      "format": "PNG",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 127,
    "pages": 3
  }
}
```

## 👤 User Management

### Create API Key

Generate a new API key for programmatic access.

**Endpoint:** `POST /user/api-keys`

**Request Body:**
```json
{
  "name": "Production API Key",
  "permissions": ["SCREENSHOT_CREATE", "SCREENSHOT_READ"],
  "rateLimit": 1000
}
```

**Response:**
```json
{
  "id": "key_abc123def456",
  "name": "Production API Key",
  "key": "sk_live_abc123def456789...",
  "keyPrefix": "sk_live_abc123",
  "permissions": ["SCREENSHOT_CREATE", "SCREENSHOT_READ"],
  "rateLimit": 1000,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### List API Keys

View all your API keys and their usage statistics.

**Endpoint:** `GET /user/api-keys`

**Response:**
```json
{
  "apiKeys": [
    {
      "id": "key_abc123def456",
      "name": "Production API Key",
      "keyPrefix": "sk_live_abc123",
      "permissions": ["SCREENSHOT_CREATE", "SCREENSHOT_READ"],
      "usageCount": 1250,
      "lastUsed": "2024-01-15T09:15:30Z",
      "createdAt": "2024-01-15T08:00:00Z"
    }
  ]
}
```

### Delete API Key

Revoke an API key permanently.

**Endpoint:** `DELETE /user/api-keys/{keyId}`

**Response:**
```json
{
  "message": "API key deleted successfully"
}
```

## 📊 Usage & Credits

### Get Usage Statistics

View your current usage and credit balance.

**Endpoint:** `GET /user/usage`

**Response:**
```json
{
  "creditsRemaining": 8750,
  "usage": {
    "thisMonth": {
      "screenshots": 1250,
      "creditsUsed": 1250
    },
    "today": {
      "screenshots": 45,
      "creditsUsed": 45
    }
  },
  "plan": {
    "name": "Professional",
    "monthlyCredits": 10000,
    "rateLimit": 100
  }
}
```

## 🔧 System Endpoints

### Health Check

Check service availability and system status.

**Endpoint:** `GET /health`

**Response:**
```json
{
  "service": "Screenshot API",
  "version": "1.0.0",
  "status": "OK",
  "timestamp": "2024-01-15T10:30:00Z",
  "environment": "production",
  "features": {
    "inMemoryDatabase": false,
    "inMemoryQueue": false,
    "localStorage": false
  }
}
```

### Service Metrics

Get detailed system performance metrics.

**Endpoint:** `GET /metrics`

**Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "workers": {
    "active": 3,
    "min": 2,
    "max": 8
  },
  "system": {
    "memory": {
      "used": 536870912,
      "free": 1073741824,
      "total": 2147483648,
      "max": 4294967296,
      "usagePercent": 25
    },
    "uptime": 3600000
  }
}
```

## ⚠️ Error Handling

All API responses follow a consistent error format:

```json
{
  "error": {
    "code": "INSUFFICIENT_CREDITS",
    "message": "Not enough credits to process request",
    "details": {
      "required": 1,
      "available": 0
    }
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "requestId": "req_abc123def456"
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_URL` | 400 | The provided URL is malformed or inaccessible |
| `INVALID_FORMAT` | 400 | Unsupported format specified |
| `INSUFFICIENT_CREDITS` | 403 | Not enough credits to complete request |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `JOB_NOT_FOUND` | 404 | Screenshot job doesn't exist |
| `PROCESSING_FAILED` | 500 | Screenshot generation failed |

## 🚀 Rate Limiting

API requests are subject to rate limiting based on your subscription plan:

- **Free Plan**: 10 requests/minute
- **Starter Plan**: 60 requests/minute  
- **Professional Plan**: 100 requests/minute
- **Enterprise Plan**: Custom limits

Rate limit headers are included in all responses:

```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642248600
```

## 🔗 Webhooks

Configure webhooks to receive real-time notifications when screenshots are completed.

### Webhook Payload

```json
{
  "event": "screenshot.completed",
  "data": {
    "jobId": "job_abc123def456",
    "status": "COMPLETED",
    "url": "https://storage.example.com/screenshots/abc123.png",
    "metadata": {
      "format": "PNG",
      "fileSize": 245760,
      "processingTime": 3200
    }
  },
  "timestamp": "2024-01-15T10:30:03Z"
}
```

### Webhook Verification

All webhooks include a signature header for verification:

```http
X-Webhook-Signature: sha256=abc123def456...
```

## 🛠️ SDKs & Libraries

**Coming Soon:**
- JavaScript/Node.js SDK
- Python SDK
- PHP SDK
- Go SDK

**Community Libraries:**
- Ruby gem (community maintained)
- .NET package (community maintained)

## 📈 Future Features (Roadmap)

- **OCR Integration**: Extract text from screenshots
- **Batch Processing**: Process multiple URLs in one request
- **Custom CSS Injection**: Modify page styling before capture
- **Video Capture**: Generate GIFs and MP4s
- **Mobile Device Simulation**: iPhone, Android viewports
- **Advanced Scheduling**: Cron-like screenshot automation

---

## 💡 Need Help?

- 📖 **Documentation**: Check our [guides](../README.md)
- 🐛 **Issues**: Report bugs on [GitHub Issues](https://github.com/screenshot-api-dev/screenshot-api/issues)
- 💬 **Discussions**: Join our [GitHub Discussions](https://github.com/screenshot-api-dev/screenshot-api/discussions)
- 📧 **Support**: Email support@screenshot-api.dev

---

*This API is built with ❤️ using Kotlin, Ktor, and Clean Architecture principles.*