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

# Use build cache for faster rebuilds
./docker/start.sh --cache

# Start only specific services
./docker/start.sh --services screenshot-api
```

### Testing
```bash
# Health check
curl http://localhost:8080/health

# Generate screenshot
curl -X POST "http://localhost:8080/api/v1/screenshot" \
  -H "Authorization: Bearer sk_development_test_key_123456789" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com", "format": "PNG"}'
```

## 🎯 Features

### Core Capabilities
- **🖼️ Screenshot Generation**: PNG, JPEG, and PDF support
- **🌐 Web Scraping**: Full-page and viewport screenshots
- **⚡ Background Processing**: Asynchronous job queue with Redis
- **🔐 API Authentication**: JWT and API key-based authentication
- **💾 Multiple Storage**: Local and cloud storage adapters
- **📊 Admin Dashboard**: User management and system monitoring
- **🚀 High Performance**: Browser pool management with Playwright

### Technical Features
- **Clean Architecture**: Hexagonal architecture with domain-driven design
- **Docker Ready**: Multi-stage builds with production-ready containers
- **Database Support**: PostgreSQL and in-memory options
- **Rate Limiting**: Token bucket algorithm for API protection
- **Health Monitoring**: Comprehensive health checks and metrics
- **CORS Support**: Cross-origin resource sharing configuration

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

# Development with build cache
./docker/start.sh --cache

# Start only specific services
./docker/start.sh --services postgres,redis

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

### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - User authentication
- `POST /api/v1/user/api-keys` - Create API key

### Screenshots
- `POST /api/v1/screenshot` - Generate screenshot
- `GET /api/v1/screenshot/{jobId}` - Get screenshot status
- `GET /api/v1/screenshots` - List user screenshots

### Admin
- `GET /api/v1/admin/users` - List users
- `GET /api/v1/admin/stats` - System statistics

### Health
- `GET /health` - Service health check

## 🐳 Production Deployment

### Docker Production Setup
```bash
# Build production image
docker-compose build --no-cache

# Start production services
docker-compose up -d

# Monitor logs
docker-compose logs -f
```

### Environment Variables
```bash
# Database
DATABASE_USE_IN_MEMORY=false
DATABASE_URL=jdbc:postgresql://postgres:5432/screenshotapi
DATABASE_USERNAME=screenshotuser
DATABASE_PASSWORD=screenshotpass

# Redis
REDIS_USE_IN_MEMORY=false
REDIS_URL=redis://redis:6379

# Storage
STORAGE_TYPE=local  # or 's3', 'gcs'
STORAGE_LOCAL_PATH=/app/screenshots

# Screenshot Service
ENABLE_PDF_GENERATION=true
BROWSER_POOL_SIZE=3
SCREENSHOT_MAX_WIDTH=1920
SCREENSHOT_MAX_HEIGHT=1080
```

## 🧪 Testing

### API Testing
```bash
# Using curl
curl -X POST "http://localhost:8080/api/v1/screenshot" \
  -H "Authorization: Bearer sk_development_test_key_123456789" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "format": "PDF",
    "fullPage": true
  }'

# Check status
curl -X GET "http://localhost:8080/api/v1/screenshot/{jobId}" \
  -H "Authorization: Bearer sk_development_test_key_123456789"
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
- [🌐 cURL Examples](examples/curl-examples.md) - Practical API usage examples
- [⚙️ Configuration Guide](docs/DEPLOYMENT.md) - Production deployment strategies *(Coming Soon)*
- [📈 Performance Tuning](docs/PERFORMANCE.md) - Optimization techniques *(Coming Soon)*

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

