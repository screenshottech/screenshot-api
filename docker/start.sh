#!/bin/bash

# Screenshot API Docker Setup Script

set -e

# Default configuration
USE_CACHE="false"
REBUILD="true"
BACKGROUND="true"
SHOW_LOGS="true"
SERVICES="all"

# Help function
show_help() {
    cat << EOF
Screenshot API Docker Setup Script

Usage: $0 [OPTIONS]

Options:
    --cache                 Use Docker build cache (default: no cache)
    --no-rebuild           Skip rebuilding images
    --foreground           Run in foreground instead of background
    --no-logs              Don't show logs after startup
    --services "SERVICE(S)" Start specific services (postgres redis, localstack, screenshot-api, all)
    --help                 Show this help message

Examples:
    $0                     # Default: build with --no-cache, run in background
    $0 --cache             # Use build cache
    $0 --no-rebuild        # Skip rebuilding, just start services
    $0 --foreground        # Run in foreground
    $0 --services postgres              # Only start PostgreSQL
    $0 --services "postgres redis"       # Start PostgreSQL and Redis
    $0 --services "localstack postgres"  # Start LocalStack and PostgreSQL
    $0 --cache --no-logs   # Use cache and don't show logs

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --cache)
            USE_CACHE="true"
            shift
            ;;
        --no-rebuild)
            REBUILD="false"
            shift
            ;;
        --foreground)
            BACKGROUND="false"
            shift
            ;;
        --no-logs)
            SHOW_LOGS="false"
            shift
            ;;
        --services)
            SERVICES="$2"
            shift 2
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

echo "🚀 Starting Screenshot API Docker Environment..."
echo "   📦 Use cache: $USE_CACHE"
echo "   🔨 Rebuild: $REBUILD"
echo "   🔄 Background: $BACKGROUND"
echo "   📋 Show logs: $SHOW_LOGS"
echo "   🎯 Services: $SERVICES"
echo ""

# Check if Docker and Docker Compose are installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Create necessary directories
echo "📁 Creating directories..."
mkdir -p screenshots logs

# Build Docker images if requested
if [ "$REBUILD" = "true" ]; then
    echo "🔨 Building Docker images..."
    if [ "$USE_CACHE" = "true" ]; then
        echo "   Using build cache..."
        docker-compose build
    else
        echo "   Building without cache..."
        docker-compose build --no-cache
    fi
fi

# Start services based on selection
start_services() {
    case $SERVICES in
        "all")
            echo "🗄️ Starting all infrastructure services..."
            docker-compose up -d postgres redis localstack
            
            echo "⏳ Waiting for services to be ready..."
            sleep 10
            
            # Check if PostgreSQL is ready
            until docker-compose exec postgres pg_isready -U screenshotuser -d screenshotapi; do
                echo "Waiting for PostgreSQL to be ready..."
                sleep 2
            done
            
            echo "⏳ Waiting for Redis to be ready..."
            until docker-compose exec redis redis-cli ping; do
                echo "Waiting for Redis to be ready..."
                sleep 2
            done
            
            echo "🚀 Starting Screenshot API service..."
            if [ "$BACKGROUND" = "true" ]; then
                docker-compose up -d screenshot-api
            else
                docker-compose up screenshot-api
            fi
            ;;
        *)
            # Handle single service or multiple services
            echo "🚀 Starting services: $SERVICES"
            
            # Check if any dependency services need to wait
            needs_postgres_wait=false
            needs_redis_wait=false
            
            if [[ $SERVICES == *"screenshot-api"* ]]; then
                needs_postgres_wait=true
                needs_redis_wait=true
            fi
            
            # Start the specified services
            if [ "$BACKGROUND" = "true" ]; then
                docker-compose up -d $SERVICES
            else
                docker-compose up $SERVICES
            fi
            
            # Wait for dependencies if needed
            if [ "$needs_postgres_wait" = "true" ] && [[ $SERVICES == *"postgres"* ]]; then
                echo "⏳ Waiting for PostgreSQL to be ready..."
                sleep 5
                until docker-compose exec postgres pg_isready -U screenshotuser -d screenshotapi; do
                    echo "Waiting for PostgreSQL to be ready..."
                    sleep 2
                done
            fi
            
            if [ "$needs_redis_wait" = "true" ] && [[ $SERVICES == *"redis"* ]]; then
                echo "⏳ Waiting for Redis to be ready..."
                until docker-compose exec redis redis-cli ping; do
                    echo "Waiting for Redis to be ready..."
                    sleep 2
                done
            fi
            ;;
    esac
}

# Start the selected services
start_services

# Show logs if running in background and logs are requested
if [ "$BACKGROUND" = "true" ] && [ "$SHOW_LOGS" = "true" ]; then
    echo "📊 Checking service health..."
    sleep 15
    
    echo "📋 Recent logs:"
    if [ "$SERVICES" = "all" ]; then
        docker-compose logs --tail=20 screenshot-api
    else
        # Show logs for all specified services
        for service in $SERVICES; do
            if docker-compose ps | grep -q "$service"; then
                echo "--- Logs for $service ---"
                docker-compose logs --tail=10 $service
            fi
        done
    fi
fi

# Show useful information only if running in background
if [ "$BACKGROUND" = "true" ]; then
    echo ""
    echo "✅ Services are starting up!"
    echo ""
    echo "🌐 Services available at:"
    if [ "$SERVICES" = "all" ] || [[ $SERVICES == *"screenshot-api"* ]]; then
        echo "  - API: http://localhost:8080"
        echo "  - Health Check: http://localhost:8080/health"
    fi
    if [ "$SERVICES" = "all" ] || [[ $SERVICES == *"postgres"* ]]; then
        echo "  - PostgreSQL: localhost:5434"
    fi
    if [ "$SERVICES" = "all" ] || [[ $SERVICES == *"redis"* ]]; then
        echo "  - Redis: localhost:6379"
    fi
    if [ "$SERVICES" = "all" ] || [[ $SERVICES == *"localstack"* ]]; then
        echo "  - LocalStack S3: http://localhost:4566"
    fi
    echo ""
    echo "📋 Useful commands:"
    echo "  - View logs: docker-compose logs -f $SERVICES"
    echo "  - Stop services: docker-compose down"
    echo "  - Restart service: docker-compose restart $SERVICES"
    echo "  - View all services: docker-compose ps"
    echo "  - Rebuild and restart: ./docker/start.sh --cache --no-logs"
    echo ""
    if [ "$SERVICES" = "all" ] || [ "$SERVICES" = "screenshot-api" ]; then
        echo "🧪 Test the API:"
        echo "  curl -X GET http://localhost:8080/health"
        echo ""
    fi
fi