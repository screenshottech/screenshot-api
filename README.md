# 📸 Screenshot API

> **A production-ready screenshot API that developers actually want to use.**

High-performance, beautifully architected screenshot generation service built with Kotlin & Ktor. From simple screenshots to complex automation workflows - designed by developers, for developers.

## 🎯 Why This API?

In a world full of screenshot services, why build another one? Because most solutions force you to choose between **simplicity** and **power**, between **quick hacks** and **maintainable code**. This API bridges that gap.

**🏗️ Architecture First**  
Showcase how Clean Architecture and Domain-Driven Design create maintainable, testable systems that scale beautifully.

**⚡ Performance Obsessed**  
Smart browser pooling, async processing, and efficient resource management ensure your screenshots generate fast, even under heavy load.

**🔧 Developer Experience**  
Self-documenting API, comprehensive examples, Docker-ready deployment, and monitoring built-in. Because your time is valuable.

**🚀 Production Ready**  
Full observability, rate limiting, audit logging, and webhook support. Deploy with confidence, scale without surprises.

Perfect for developers who need reliable screenshot automation without vendor lock-in or black box solutions.

## 🚀 Quick Start

### Local Development
```bash
./gradlew run
```

### Docker Environment (Recommended)
```bash
# Start all services (PostgreSQL, Redis, API)
./docker/start.sh

# Start only infrastructure services (PostgreSQL, Redis)
./docker/start-infra.sh

# Start with Docker Compose for production
docker-compose -f docker/docker-compose.dokploy.yml up -d

# Stop all services
./docker/stop.sh
```

### Testing
```bash
# Health check
curl http://localhost:8080/health

# Generate screenshot (using API key)
curl -X POST "http://localhost:8080/api/v1/screenshots" \
  -H "X-API-Key: sk_development_test_key_123456789" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com", "format": "PNG"}'

# Check screenshot status
curl -X GET "http://localhost:8080/api/v1/screenshots/{jobId}" \
  -H "X-API-Key: sk_development_test_key_123456789"
```

## 🎯 Features

### Core Capabilities
- **🖼️ Screenshot Generation**: PNG, JPEG, WEBP, and PDF support
- **🌐 Web Scraping**: Full-page and viewport screenshots
- **⚡ Background Processing**: Asynchronous job queue with Redis
- **🔐 Multi-Authentication**: JWT, API key, and API key ID + JWT authentication
- **💾 Multiple Storage**: Local and AWS S3 storage adapters
- **📊 Admin Dashboard**: User management and system monitoring
- **🚀 High Performance**: Browser pool management with Playwright
- **🔔 Webhook System**: Optimized event notifications with 60-80% traffic reduction
- **💳 Billing Integration**: Stripe integration with subscription management
- **📈 Usage Analytics**: Timeline analytics with success rates and trends

### Technical Features
- **Clean Architecture**: Hexagonal architecture with domain-driven design
- **Docker Ready**: Multi-stage builds with production-ready containers
- **Database Support**: PostgreSQL with HikariCP connection pooling
- **Rate Limiting**: Multi-level rate limiting with plan-based quotas
- **Health Monitoring**: Comprehensive health checks and metrics
- **CORS Support**: Cross-origin resource sharing configuration
- **Retry System**: Smart retry logic with manual retry capabilities
- **Credit System**: Flexible billing with multiple job types and costs
- **OpenAPI Documentation**: Interactive Swagger UI with comprehensive API docs

## 🛠️ Development

### Prerequisites
- **Java 17** or higher
- **Docker & Docker Compose** (for containerized development)
- **PostgreSQL** (optional, can use in-memory database)
- **Redis** (optional, can use in-memory queue)

### Local Development
```bash
# Run with in-memory database and queue
./gradlew run

# Run tests
./gradlew test

# Build JAR
./gradlew build
```

### Docker Development (Recommended)
```bash
# Start all services
./docker/start.sh

# Start only infrastructure services (PostgreSQL, Redis)
./docker/start-infra.sh

# Start with specific Docker Compose configuration
docker-compose -f docker/docker-compose.dokploy.yml up -d

# View logs
docker-compose logs -f screenshot-api

# Stop services
./docker/stop.sh
```

## 🏗️ Architecture

Built with **Clean Architecture** (Hexagonal Architecture) and **Domain-Driven Design** principles:

```
┌─────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                Application Layer                    │   │
│  │  ┌─────────────────────────────────────────────┐   │   │
│  │  │              Domain Layer                   │   │   │
│  │  │                                             │   │   │
│  │  │  • Entities (User, Screenshot, ApiKey)     │   │   │
│  │  │  • Value Objects (ScreenshotRequest)       │   │   │
│  │  │  • Repository Interfaces                   │   │   │
│  │  │  • Domain Services & Exceptions            │   │   │
│  │  │                                             │   │   │
│  │  └─────────────────────────────────────────────┘   │   │
│  │                                                     │   │
│  │  • Use Cases (Business Logic)                      │   │
│  │  • Input/Output Ports                              │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  • REST Controllers & Authentication                       │
│  • Database Adapters (PostgreSQL, InMemory)               │
│  • External Services (Playwright, Storage, Cache)         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Key Benefits:**
- **Framework Independence**: Core logic isolated from external dependencies
- **Testability**: Business rules testable without infrastructure
- **Flexibility**: Easy to swap databases, frameworks, or external services
- **Maintainability**: Clear separation of concerns and dependencies

See our detailed [Architecture Guide](docs/ARCHITECTURE.md) for deep-dive technical documentation.

## 📡 API Endpoints

### 🔐 Authentication & User Management
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - User authentication
- `GET /api/v1/auth/providers` - List available auth providers
- `POST /api/v1/auth/{provider}/login` - Provider-specific login (Clerk, etc.)
- `GET /api/v1/user/profile` - Get user profile
- `PUT /api/v1/user/profile` - Update user profile
- `GET /api/v1/user/usage` - Get usage statistics
- `GET /api/v1/user/usage/timeline` - Timeline analytics with periods (7d, 30d, 90d, 1y)

### 🔑 API Key Management
- `GET /api/v1/user/api-keys` - List API keys (masked)
- `POST /api/v1/user/api-keys` - Create API key
- `PATCH /api/v1/user/api-keys/{keyId}` - Update API key status
- `DELETE /api/v1/user/api-keys/{keyId}` - Delete API key

### 📸 Screenshots
- `POST /api/v1/screenshots` - Generate screenshot (supports PNG, JPEG, WEBP, PDF)
- `GET /api/v1/screenshots/{jobId}` - Get screenshot status
- `GET /api/v1/screenshots` - List user screenshots with pagination
- `POST /api/v1/screenshots/status/bulk` - Bulk status check for efficient polling
- `POST /api/v1/screenshots/{jobId}/retry` - Manual retry failed/stuck jobs

### 💳 Billing & Subscriptions
- `GET /api/v1/billing/plans` - Get available plans
- `POST /api/v1/billing/checkout` - Create Stripe checkout session
- `GET /api/v1/billing/subscription` - Get user subscription
- `POST /api/v1/billing/portal` - Create billing portal session
- `POST /api/v1/billing/webhook` - Stripe webhook handler

### 🔔 Webhooks
- `POST /api/v1/webhooks` - Create webhook configuration
- `GET /api/v1/webhooks` - List user webhooks
- `GET /api/v1/webhooks/{id}` - Get webhook details
- `PUT /api/v1/webhooks/{id}` - Update webhook
- `DELETE /api/v1/webhooks/{id}` - Delete webhook
- `POST /api/v1/webhooks/{id}/test` - Test webhook (rate limited: 1/min/user)
- `POST /api/v1/webhooks/{id}/regenerate-secret` - Regenerate webhook secret
- `GET /api/v1/webhooks/{id}/deliveries` - Get webhook deliveries
- `GET /api/v1/webhooks/events` - List available webhook events

### 👨‍💼 Admin Management
- `GET /api/v1/admin/users` - List users with pagination
- `GET /api/v1/admin/users/{userId}` - Get user details
- `GET /api/v1/admin/stats` - System statistics
- `GET /api/v1/admin/subscriptions` - List all subscriptions
- `POST /api/v1/admin/users/{userId}/synchronize-plan` - Sync user plan

### 🔍 System Health
- `GET /health` - Service health check
- `GET /status` - Health check with version info
- `GET /ready` - Readiness check for dependencies
- `GET /metrics` - System metrics

## 🐳 Production Deployment

### Docker Production Setup
```bash
# Build production image with Dokploy configuration
docker-compose -f docker/docker-compose.dokploy.yml build --no-cache

# Start production services
docker-compose -f docker/docker-compose.dokploy.yml up -d

# Monitor logs
docker-compose -f docker/docker-compose.dokploy.yml logs -f

# Access Swagger UI
curl http://localhost:8080/swagger
```

### Environment Variables
```bash
# Core System
ENVIRONMENT=PRODUCTION                              # LOCAL, DEVELOPMENT, STAGING, PRODUCTION, DOCKER
HOST=0.0.0.0                                       # Server host
PORT=8080                                          # Server port

# Database Configuration
DATABASE_USE_IN_MEMORY=false                       # Use PostgreSQL for production
DATABASE_URL=jdbc:postgresql://postgres:5432/screenshotapi
DATABASE_USERNAME=screenshotuser
DATABASE_PASSWORD=screenshotpass
DB_POOL_SIZE=20                                    # HikariCP connection pool size

# Redis Configuration
REDIS_USE_IN_MEMORY=false                          # Use Redis for production
REDIS_URL=redis://redis:6379
REDIS_MAX_CONNECTIONS=10                           # Max Redis connections

# JWT & Authentication (CRITICAL for production)
JWT_SECRET=your-secure-jwt-secret-min-32-chars     # Generate with: openssl rand -base64 32
JWT_ISSUER=screenshotapi-api                       # JWT issuer
JWT_AUDIENCE=screenshotapi-api-users               # JWT audience
JWT_EXPIRATION_HOURS=24                            # JWT expiration

# API Key Configuration
API_KEY_PREFIX=sk_                                 # API key prefix
API_KEY_LENGTH=32                                  # API key length (excluding prefix)

# Storage Configuration
STORAGE_USE_LOCAL=false                            # Use S3 for production
STORAGE_LOCAL_PATH=/app/screenshots                # Local storage path
S3_BUCKET=your-s3-bucket                          # S3 bucket name
S3_REGION=us-east-1                               # S3 region
AWS_ACCESS_KEY_ID=your-aws-access-key             # AWS access key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key         # AWS secret key

# Screenshot Service Configuration
ENABLE_PDF_GENERATION=true                         # Enable PDF generation
BROWSER_POOL_SIZE=3                               # Browser pool size
SCREENSHOT_MAX_WIDTH=1920                         # Max screenshot width
SCREENSHOT_MAX_HEIGHT=1080                        # Max screenshot height
SCREENSHOT_DEFAULT_TIMEOUT=30000                  # Default timeout (30s)
SCREENSHOT_MAX_TIMEOUT=60000                      # Max timeout (60s)

# Webhook Configuration (Traffic Optimized)
WEBHOOK_MAX_RETRY_ATTEMPTS=3                      # Production webhook retries
WEBHOOK_MAX_TEST_RETRY_ATTEMPTS=1                 # Test webhook retries (80% traffic reduction)
WEBHOOK_RETRY_DELAY_MINUTES=1,5,15,30,60         # Progressive retry delays
WEBHOOK_TEST_RATE_LIMIT_MINUTES=1                 # Rate limit: 1 test/min/user
WEBHOOK_TIMEOUT_SECONDS=30                        # HTTP timeout

# Billing (Stripe Integration)
STRIPE_SECRET_KEY=sk_live_your-stripe-secret      # Stripe secret key
STRIPE_PUBLISHABLE_KEY=pk_live_your-stripe-pub    # Stripe publishable key
STRIPE_WEBHOOK_SECRET=whsec_your-webhook-secret   # Stripe webhook secret

# Authentication Providers
ENABLED_AUTH_PROVIDERS=local,clerk                # Supported auth providers
DEFAULT_AUTH_PROVIDER=local                       # Default auth provider
CLERK_DOMAIN=your-app.clerk.accounts.dev          # Clerk domain (if using Clerk)

# Rate Limiting
RATE_LIMITING_ENABLED=true                        # Enable rate limiting
RATE_LIMIT_CAPACITY=1000                          # Global rate limit
RATE_LIMIT_RATE_SECONDS=60                        # Rate limit window

# Logging & Monitoring
LOG_LEVEL=INFO                                    # Log level (TRACE, DEBUG, INFO, WARN, ERROR)
```

## 🧪 Testing

### API Testing
```bash
# Using curl with API key
curl -X POST "http://localhost:8080/api/v1/screenshots" \
  -H "X-API-Key: sk_development_test_key_123456789" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "format": "PDF",
    "fullPage": true
  }'

# Check status
curl -X GET "http://localhost:8080/api/v1/screenshots/{jobId}" \
  -H "X-API-Key: sk_development_test_key_123456789"

# Bulk status check for multiple jobs
curl -X POST "http://localhost:8080/api/v1/screenshots/status/bulk" \
  -H "X-API-Key: sk_development_test_key_123456789" \
  -H "Content-Type: application/json" \
  -d '{"jobIds": ["job1", "job2", "job3"]}'

# Manual retry failed job
curl -X POST "http://localhost:8080/api/v1/screenshots/{jobId}/retry" \
  -H "X-API-Key: sk_development_test_key_123456789"
```

### Unit Tests
```bash
./gradlew test
```

## 📚 Documentation

### Getting Started
- [🐳 Docker Setup Guide](docker/README.md) - Complete Docker deployment guide
- [📡 API Reference](docs/API_REFERENCE.md) - Comprehensive API documentation
- [🏗️ Architecture Deep-Dive](docs/ARCHITECTURE.md) - Clean Architecture implementation guide

### Advanced Topics
- [🤝 Contributing Guide](CONTRIBUTING.md) - How to contribute to the project
- [🔔 Webhook Implementation](docs/WEBHOOK_IMPLEMENTATION.md) - Webhook system guide
- [🔄 Job Retry System](docs/JOB_RETRY_SYSTEM.md) - Manual and automatic retry documentation
- [🔐 Authentication Flow](docs/AUTHENTICATION_FLOW.md) - Multi-provider authentication guide

## 🛣️ Roadmap

### 🔮 Upcoming Features
- **🔍 OCR Integration**: Extract text content from screenshots using advanced recognition
- **📱 Mobile Simulation**: iPhone, Android device viewport simulation
- **🎥 Video Capture**: Generate animated GIFs and MP4 recordings
- **⚡ Batch Processing**: Process multiple URLs in a single request
- **🎨 Custom CSS Injection**: Modify page styling before capture
- **⏰ Scheduled Screenshots**: Cron-like automation for recurring captures
- **🌍 Multi-region Deployment**: Global edge processing for faster response times

### 🔧 Technical Improvements
- **📊 Enhanced Metrics**: Detailed performance analytics and insights
- **🛡️ Advanced Security**: Role-based access control and API quotas
- **🔄 Auto-scaling**: Dynamic worker pool management
- **📦 SDK Libraries**: Official client libraries for popular languages

## 🤝 Contributing

We welcome contributions! Whether you're fixing bugs, adding features, or improving documentation, every contribution helps make this API better.

**Quick Start:**
1. 🍴 Fork the repository
2. 🌿 Create feature branch (`git checkout -b feature/amazing-feature`)
3. ✨ Make your changes with tests
4. 📝 Update documentation if needed
5. 🚀 Submit a Pull Request

See our [Contributing Guide](CONTRIBUTING.md) for detailed guidelines.

## 💡 Show Your Support

Give a ⭐️ if this project helped you! It helps others discover this API.

## 📞 Connect & Support

- 🐛 **Found a bug?** [Open an issue](https://github.com/screenshot-api-dev/screenshot-api/issues)
- 💭 **Have an idea?** [Start a discussion](https://github.com/screenshot-api-dev/screenshot-api/discussions)
- 📧 **Need help?** Email screenshotapi.dev@gmail.com
- 🐦 **Follow updates** [@screenshot_api_dev](https://twitter.com/screenshot_api_dev)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🚀 Ready to Deploy?

If the server starts successfully, you'll see:

```
2025-05-28 15:38:39.818 [main] INFO  Application - Application started in 13.623 seconds.
2025-05-28 15:38:39.873 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

**🎉 Your screenshot API is now live and ready to capture the web!**

*Built with ❤️ using Kotlin, Ktor, and Clean Architecture principles.*

