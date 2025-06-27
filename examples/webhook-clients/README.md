# Webhook Client Examples

Example implementations of webhook receivers for the Screenshot API in different programming languages.

## Available Examples

### Node.js Webhook Server
**Location**: `nodejs/`
**Description**: Complete webhook receiver with HMAC validation, web dashboard, and multiple test endpoints.

**Features**:
- ✅ HMAC-SHA256 signature verification
- 📊 Real-time statistics dashboard
- 🧪 Multiple test endpoints (success, error, random)
- 📋 Webhook history tracking
- 🌐 Web interface at http://localhost:3001

**Quick Start**:
```bash
cd nodejs/
npm install
npm start
```

## Planned Examples

### Java/Spring Boot
Example Spring Boot application with webhook endpoint.

### Python/FastAPI
Example FastAPI application with async webhook handling.

## Usage Patterns

All examples demonstrate:

1. **HMAC Signature Validation** - Verifying webhook authenticity
2. **Duplicate Prevention** - Using delivery IDs to prevent reprocessing
3. **Error Handling** - Proper HTTP status codes and responses
4. **Event Processing** - Handling different webhook event types
5. **Logging** - Comprehensive request/response logging

## Security Notes

These examples are for **development and testing only**. For production use:

- Use HTTPS endpoints
- Implement proper authentication beyond HMAC
- Add rate limiting and DDoS protection
- Store webhook secrets securely
- Validate all input data thoroughly
- Implement proper error handling and monitoring

## Integration Guide

1. **Choose an example** that matches your tech stack
2. **Review the code** to understand the implementation
3. **Adapt for your use case** (event handling, data processing, etc.)
4. **Test with the Screenshot API** using the provided test endpoints
5. **Implement additional security measures** for production
