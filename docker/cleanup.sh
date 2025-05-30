#!/bin/bash

# Screenshot API Docker Cleanup Script
set -e

# Parse command line arguments
REMOVE_VOLUMES=false
SPECIFIC_SERVICE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --volumes|-v)
            REMOVE_VOLUMES=true
            shift
            ;;
        --service|-s)
            SPECIFIC_SERVICE="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "🧹 Cleaning up Screenshot API Docker Environment..."

# Stop containers
if [ -n "$SPECIFIC_SERVICE" ]; then
    echo "🛑 Stopping $SPECIFIC_SERVICE service..."
    docker-compose stop "$SPECIFIC_SERVICE"
else
    echo "🛑 Stopping all containers..."
    docker-compose down
fi

# Remove images
echo "🗑️ Removing Docker images..."
if [ -n "$SPECIFIC_SERVICE" ]; then
    docker-compose rm -f "$SPECIFIC_SERVICE"
else
    docker-compose down --rmi all
fi

# Handle volumes
if [ "$REMOVE_VOLUMES" = true ]; then
    echo "💾 Removing volumes..."
    if [ -n "$SPECIFIC_SERVICE" ]; then
        case "$SPECIFIC_SERVICE" in
            postgres)
                docker volume rm screenshot-api_postgres-data
                ;;
            redis)
                docker volume rm screenshot-api_redis-data
                ;;
        esac
    else
        docker-compose down --volumes
    fi
fi

# Clean unused resources
echo "🧽 Cleaning up unused Docker resources..."
docker system prune -f

echo "✅ Cleanup completed!"
echo ""
echo "⚠️  Note: Database and Redis data volumes are preserved unless --volumes flag was used."
