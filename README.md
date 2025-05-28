# Screenshot API Service

A high-performance screenshot generation API built with Ktor, implementing Clean Architecture principles. This service provides automated screenshot and PDF generation capabilities using Microsoft Playwright.

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
- **Java 21** or higher
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

```
src/
├── main/kotlin/com/screenshotapi/
│   ├── core/                    # Business logic (Clean Architecture)
│   │   ├── domain/             # Domain entities and repositories
│   │   ├── usecases/           # Business use cases
│   │   └── services/           # Domain services
│   ├── infrastructure/         # External concerns
│   │   ├── adapters/           # Input/Output adapters
│   │   ├── config/             # Configuration
│   │   └── services/           # Infrastructure services
│   └── workers/                # Background job processing
└── docker/                     # Docker configuration
    ├── start.sh               # Start script with options
    ├── stop.sh                # Stop script with options
    └── README.md              # Docker documentation
```

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

- [Docker Setup Guide](docker/README.md)
- [API Documentation](.http/api.http)
- [Architecture Overview](docs/architecture.md)

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License.

---

If the server starts successfully, you'll see the following output:

```
2025-05-28 15:38:39.818 [main] INFO  Application - Application started in 13.623 seconds.
2025-05-28 15:38:39.873 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

