#!/bin/bash

# Script to start only infrastructure services (PostgreSQL, Redis, LocalStack)
# Without building the API

set -e

echo "🚀 Starting infrastructure services only..."
echo ""

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed."
    exit 1
fi

# Create necessary directories
echo "📁 Creating directories..."
mkdir -p screenshots logs

# Start only infrastructure services
echo "🗄️ Starting PostgreSQL, Redis and LocalStack..."
docker-compose up -d postgres redis localstack

echo "⏳ Waiting for services to be ready..."

# Wait for PostgreSQL
echo "🐘 Waiting for PostgreSQL..."
until docker-compose exec postgres pg_isready -U screenshotuser -d screenshotapi &>/dev/null; do
    echo "   PostgreSQL starting..."
    sleep 2
done
echo "   ✅ PostgreSQL ready"

# Wait for Redis
echo "📦 Waiting for Redis..."
until docker-compose exec redis redis-cli ping &>/dev/null; do
    echo "   Redis starting..."
    sleep 2
done
echo "   ✅ Redis ready"

# Wait for LocalStack
echo "☁️ Waiting for LocalStack..."
sleep 5
until curl -s http://localhost:4566/_localstack/health &>/dev/null; do
    echo "   LocalStack starting..."
    sleep 3
done
echo "   ✅ LocalStack ready"

echo ""
echo "✅ Infrastructure ready!"
echo ""
echo "🌐 Services available at:"
echo "  - PostgreSQL: localhost:5434"
echo "  - Redis: localhost:6379"
echo "  - LocalStack S3: http://localhost:4566"
echo ""
echo "🚀 To start the API locally:"
echo "  ./gradlew run"
echo ""
echo "📋 Useful commands:"
echo "  - View logs: docker-compose logs -f postgres redis localstack"
echo "  - Stop services: docker-compose stop postgres redis localstack"
echo "  - Status: docker-compose ps"
echo ""