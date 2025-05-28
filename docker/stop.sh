#!/bin/bash

# Screenshot API Docker Stop Script

set -e

# Default configuration
SERVICES="all"
REMOVE_VOLUMES="false"
REMOVE_IMAGES="false"

# Help function
show_help() {
    cat << EOF
Screenshot API Docker Stop Script

Usage: $0 [OPTIONS]

Options:
    --services SERVICE     Stop specific services (postgres,redis,screenshot-api,all)
    --volumes              Remove volumes (WARNING: deletes all data)
    --images               Remove images after stopping
    --help                 Show this help message

Examples:
    $0                     # Stop all services
    $0 --services postgres # Only stop PostgreSQL
    $0 --volumes           # Stop all services and remove volumes
    $0 --images            # Stop all services and remove images

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --services)
            SERVICES="$2"
            shift 2
            ;;
        --volumes)
            REMOVE_VOLUMES="true"
            shift
            ;;
        --images)
            REMOVE_IMAGES="true"
            shift
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

echo "🛑 Stopping Screenshot API Docker Environment..."
echo "   🎯 Services: $SERVICES"
echo "   💾 Remove volumes: $REMOVE_VOLUMES"
echo "   🖼️ Remove images: $REMOVE_IMAGES"
echo ""

# Stop services based on selection
case $SERVICES in
    "postgres")
        echo "🛑 Stopping PostgreSQL..."
        docker-compose stop postgres
        ;;
    "redis")
        echo "🛑 Stopping Redis..."
        docker-compose stop redis
        ;;
    "screenshot-api")
        echo "🛑 Stopping Screenshot API..."
        docker-compose stop screenshot-api
        ;;
    "all")
        echo "🛑 Stopping all services..."
        docker-compose down
        ;;
    *)
        echo "❌ Unknown service: $SERVICES"
        echo "Available services: postgres, redis, screenshot-api, all"
        exit 1
        ;;
esac

# Remove volumes if requested
if [ "$REMOVE_VOLUMES" = "true" ]; then
    echo "⚠️  Removing volumes (this will delete all data)..."
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down --volumes
        echo "💾 Volumes removed"
    else
        echo "Skipping volume removal"
    fi
fi

# Remove images if requested
if [ "$REMOVE_IMAGES" = "true" ]; then
    echo "🗑️ Removing images..."
    docker-compose down --rmi all
    echo "🖼️ Images removed"
fi

echo "✅ Stop completed!"