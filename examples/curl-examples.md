# 🌐 cURL Examples

Practical examples for testing and integrating with the Screenshot API using cURL.

## 🔐 Authentication Setup

First, export your API key for easier usage:

```bash
export API_KEY="sk_development_test_key_123456789"
export BASE_URL="http://localhost:8080/api/v1"
```

## 📸 Basic Screenshots

### Simple PNG Screenshot
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "format": "PNG"
  }'
```

### Full-Page Screenshot
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://github.com",
    "format": "PNG",
    "fullPage": true,
    "waitTime": 3000
  }'
```

### Custom Viewport Size
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://tailwindui.com",
    "format": "PNG",
    "width": 1920,
    "height": 1080,
    "waitTime": 2000
  }'
```

## 📄 PDF Generation

### Simple PDF
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://docs.github.com",
    "format": "PDF",
    "fullPage": true
  }'
```

### Mobile-Optimized Screenshot
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://m.facebook.com",
    "format": "PNG",
    "width": 375,
    "height": 812,
    "waitTime": 2000
  }'
```

## 🔄 Job Management

### Check Screenshot Status
```bash
# Replace JOB_ID with actual job ID from creation response
JOB_ID="job_abc123def456"

curl -X GET "$BASE_URL/screenshot/$JOB_ID" \
  -H "Authorization: Bearer $API_KEY"
```

### List Your Screenshots
```bash
curl -X GET "$BASE_URL/screenshots" \
  -H "Authorization: Bearer $API_KEY"
```

### List with Pagination
```bash
curl -X GET "$BASE_URL/screenshots?page=2&limit=10" \
  -H "Authorization: Bearer $API_KEY"
```

### Filter by Status
```bash
curl -X GET "$BASE_URL/screenshots?status=COMPLETED&limit=20" \
  -H "Authorization: Bearer $API_KEY"
```

## 🔗 Webhooks

### Screenshot with Webhook Notification
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://stripe.com/docs",
    "format": "PNG",
    "fullPage": true,
    "webhookUrl": "https://your-app.com/webhooks/screenshot-complete"
  }'
```

## 🔑 API Key Management

### Create New API Key
```bash
curl -X POST "$BASE_URL/user/api-keys" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Production API Key",
    "permissions": ["SCREENSHOT_CREATE", "SCREENSHOT_READ"],
    "rateLimit": 1000
  }'
```

### List API Keys
```bash
curl -X GET "$BASE_URL/user/api-keys" \
  -H "Authorization: Bearer $API_KEY"
```

### Delete API Key
```bash
# Replace KEY_ID with actual key ID
KEY_ID="key_abc123def456"

curl -X DELETE "$BASE_URL/user/api-keys/$KEY_ID" \
  -H "Authorization: Bearer $API_KEY"
```

## 📊 Usage Statistics

### Get Current Usage
```bash
curl -X GET "$BASE_URL/user/usage" \
  -H "Authorization: Bearer $API_KEY"
```

## 🏥 Health Checks

### API Health Check
```bash
curl -X GET "http://localhost:8080/health"
```

### Detailed Metrics
```bash
curl -X GET "http://localhost:8080/metrics"
```

## 🎯 Advanced Examples

### E-commerce Product Screenshot
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.amazon.com/dp/B08N5WRWNW",
    "format": "PNG",
    "width": 1200,
    "height": 800,
    "waitTime": 5000,
    "webhookUrl": "https://your-ecommerce.com/product-screenshots"
  }'
```

### Blog Post to PDF
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://dev.to/your-article",
    "format": "PDF",
    "fullPage": true,
    "waitTime": 3000
  }'
```

### Landing Page Monitoring
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://your-landing-page.com",
    "format": "PNG",
    "width": 1920,
    "height": 1080,
    "waitTime": 2000,
    "webhookUrl": "https://monitoring.your-app.com/page-screenshots"
  }'
```

## 🐛 Error Handling Examples

### Handle Rate Limiting
```bash
# This will show rate limit headers
curl -v -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "format": "PNG"
  }' 2>&1 | grep -E "(X-RateLimit|HTTP)"
```

### Invalid URL Example
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "not-a-valid-url",
    "format": "PNG"
  }'
```

### Unauthorized Request
```bash
curl -X POST "$BASE_URL/screenshot" \
  -H "Authorization: Bearer invalid_key" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "format": "PNG"
  }'
```

## 🚀 Batch Processing Script

Here's a bash script to process multiple URLs:

```bash
#!/bin/bash

API_KEY="your_api_key_here"
BASE_URL="http://localhost:8080/api/v1"

URLS=(
  "https://github.com"
  "https://stackoverflow.com"
  "https://dev.to"
  "https://hackernews.com"
)

for url in "${URLS[@]}"; do
  echo "Processing: $url"
  
  response=$(curl -s -X POST "$BASE_URL/screenshot" \
    -H "Authorization: Bearer $API_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"url\": \"$url\",
      \"format\": \"PNG\",
      \"fullPage\": true
    }")
  
  job_id=$(echo $response | jq -r '.jobId')
  echo "Job created: $job_id"
  
  # Wait a bit between requests to avoid rate limiting
  sleep 2
done
```

## 💡 Tips

1. **Rate Limiting**: Check `X-RateLimit-*` headers to monitor your usage
2. **Webhooks**: Use webhooks for long-running jobs instead of polling
3. **Error Handling**: Always check HTTP status codes and error responses
4. **Testing**: Use the health endpoint to verify API availability
5. **Authentication**: Keep your API keys secure and rotate them regularly

---

*Need more examples? Check our [API Reference](../docs/API_REFERENCE.md) for complete documentation.*