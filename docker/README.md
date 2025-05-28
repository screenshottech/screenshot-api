# Docker Setup for Screenshot API

This directory contains Docker configuration for running the Screenshot API in a containerized environment with PostgreSQL and Redis.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- At least 4GB of available RAM
- At least 10GB of available disk space

## Quick Start

### 1. Start all services
```bash
./docker/start.sh
```

This script will:
- Build the application Docker image (with --no-cache by default)
- Start PostgreSQL with initialized schema (port 5434)
- Start Redis for caching and queues (port 6379)
- Start the Screenshot API service (port 8080)
- Show service status and logs

### Advanced Usage
```bash
# Use build cache for faster rebuilds
./docker/start.sh --cache

# Skip rebuilding, just start services
./docker/start.sh --no-rebuild

# Start only specific services
./docker/start.sh --services postgres
./docker/start.sh --services redis
./docker/start.sh --services screenshot-api

# Run in foreground for debugging
./docker/start.sh --foreground

# Combine options
./docker/start.sh --cache --no-logs --services screenshot-api

# See all available options
./docker/start.sh --help
```

### 2. Verify services are running
```bash
# Check all services
docker-compose ps

# Check service health
curl http://localhost:8080/health

# View API logs
docker-compose logs -f screenshot-api
```

### 3. Test the API
```bash
# Test screenshot generation
curl -X POST "http://localhost:8080/api/v1/screenshot" \
  -H "Authorization: Bearer sk_development_test_key_123456789" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "format": "PNG",
    "width": 1920,
    "height": 1080
  }'
```

## Services

### Screenshot API
- **Port**: 8080
- **Health Check**: http://localhost:8080/health
- **Environment**: Production-like with PostgreSQL and Redis

### PostgreSQL Database
- **Port**: 5434 (external), 5432 (internal)
- **Database**: screenshotapi
- **User**: screenshotuser
- **Password**: screenshotpass

### Redis Cache/Queue
- **Port**: 6379
- **Usage**: Caching and background job queues

## Environment Variables

The application supports these environment variables:

### Database
- `DATABASE_USE_IN_MEMORY`: false (uses PostgreSQL)
- `DATABASE_URL`: PostgreSQL connection string
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password

### Redis
- `REDIS_USE_IN_MEMORY`: false (uses Redis)
- `REDIS_URL`: Redis connection string

### JWT Authentication
- `JWT_SECRET`: Secret key for JWT tokens
- `JWT_ISSUER`: JWT issuer
- `JWT_AUDIENCE`: JWT audience
- `JWT_EXPIRATION_HOURS`: Token expiration time

### Screenshot Service
- `ENABLE_PDF_GENERATION`: Enable/disable PDF generation
- `BROWSER_POOL_SIZE`: Number of browser instances
- `SCREENSHOT_MAX_WIDTH`: Maximum screenshot width
- `SCREENSHOT_MAX_HEIGHT`: Maximum screenshot height

## Useful Commands

### Managing Services with Scripts
```bash
# Start services with different options
./docker/start.sh                    # Default: build without cache, all services
./docker/start.sh --cache           # Use build cache
./docker/start.sh --no-rebuild      # Skip building
./docker/start.sh --services redis  # Start only Redis

# Stop services
./docker/stop.sh                    # Stop all services
./docker/stop.sh --services postgres # Stop only PostgreSQL
./docker/stop.sh --volumes          # Stop and remove volumes (deletes data)
./docker/stop.sh --images           # Stop and remove images

# View help
./docker/start.sh --help
./docker/stop.sh --help
```

### Direct Docker Compose Commands
```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# Restart a specific service
docker-compose restart screenshot-api

# View logs
docker-compose logs -f screenshot-api
docker-compose logs -f postgres
docker-compose logs -f redis

# Execute commands in containers
docker-compose exec postgres psql -U screenshotuser -d screenshotapi
docker-compose exec redis redis-cli
```

### Database Operations
```bash
# Connect to PostgreSQL (note: port 5434 externally)
docker-compose exec postgres psql -U screenshotuser -d screenshotapi

# Connect from host machine
psql -h localhost -p 5434 -U screenshotuser -d screenshotapi

# View tables
docker-compose exec postgres psql -U screenshotuser -d screenshotapi -c "\\dt"

# Backup database
docker-compose exec postgres pg_dump -U screenshotuser screenshotapi > backup.sql

# Restore database
cat backup.sql | docker-compose exec -T postgres psql -U screenshotuser -d screenshotapi
```

### Monitoring and Debugging
```bash
# Check container resource usage
docker stats

# Inspect container configuration
docker inspect screenshot-api

# View container filesystem
docker-compose exec screenshot-api ls -la /app

# Access container shell
docker-compose exec screenshot-api sh
```

## Volumes and Data Persistence

### Persistent Volumes
- `postgres_data`: PostgreSQL database files
- `redis_data`: Redis data files
- `screenshot_files`: Generated screenshot files
- `app_logs`: Application log files

### Accessing Generated Screenshots
Screenshots are stored in the `screenshot_files` volume and accessible via:
- Container path: `/app/screenshots`
- HTTP endpoint: `http://localhost:8080/files/`

## Configuration Files

- `docker-compose.yml`: Main service orchestration
- `Dockerfile`: Multi-stage application build
- `init.sql`: Database schema initialization
- `docker-compose.env`: Environment variables
- `start.sh`: Setup and start script
- `cleanup.sh`: Cleanup script

## Troubleshooting

### Service Won't Start
```bash
# Check service logs
docker-compose logs screenshot-api

# Check if ports are available
netstat -an | grep :8080
netstat -an | grep :5432
netstat -an | grep :6379

# Restart with fresh containers
docker-compose down
docker-compose up --build -d
```

### Database Connection Issues
```bash
# Check if PostgreSQL is ready
docker-compose exec postgres pg_isready -U screenshotuser

# Check database connectivity from app
docker-compose exec screenshot-api nc -zv postgres 5432
```

### Performance Issues
```bash
# Check resource usage
docker stats

# Increase memory limits in docker-compose.yml
# Reduce browser pool size in environment variables
```

### Cleanup and Reset
```bash
# Stop and remove everything
./docker/cleanup.sh

# Stop services with different options
./docker/stop.sh --volumes  # Remove volumes (deletes data)
./docker/stop.sh --images   # Remove images

# Full cleanup (careful!)
docker-compose down --volumes --rmi all
```

## Production Considerations

### Security
- Change default passwords in production
- Use Docker secrets for sensitive configuration
- Enable firewall rules for container access
- Use HTTPS/TLS for external access

### Performance
- Increase memory limits for high-load scenarios
- Consider using external PostgreSQL/Redis services
- Implement horizontal scaling with container orchestration
- Monitor resource usage and adjust accordingly

### Monitoring
- Set up log aggregation (ELK stack, Fluentd)
- Implement health checks and alerting
- Use monitoring tools (Prometheus, Grafana)
- Set up distributed tracing for debugging