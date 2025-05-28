#!/bin/bash

# Screenshot API Docker Cleanup Script

set -e

echo "🧹 Cleaning up Screenshot API Docker Environment..."

# Stop and remove containers
echo "🛑 Stopping containers..."
docker-compose down

# Remove images
echo "🗑️ Removing Docker images..."
docker-compose down --rmi all

# Remove volumes (optional - uncomment if you want to delete data)
# echo "💾 Removing volumes..."
# docker-compose down --volumes

# Remove unused Docker resources
echo "🧽 Cleaning up unused Docker resources..."
docker system prune -f

echo "✅ Cleanup completed!"
echo ""
echo "⚠️  Note: Database and Redis data volumes are preserved."
echo "   To remove all data, run: docker-compose down --volumes"