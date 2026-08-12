#!/bin/bash

# RAG Application Quick Start Script

set -e

echo "🚀 RAG Application Quick Start"
echo "==============================="
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "⚠️  .env file not found. Creating from .env.example..."
    cp .env.example .env
    echo "✅ Created .env file"
    echo ""
    echo "⚠️  IMPORTANT: Edit .env and add your GEMINI_API_KEY before continuing!"
    echo ""
    read -p "Press Enter once you've updated .env with your API key..."
fi

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop and try again."
    exit 1
fi

echo "✅ Docker is running"
echo ""

# Ask user which mode to run
echo "Choose deployment mode:"
echo "1) Full Stack (Docker) - PostgreSQL + Java App in Docker"
echo "2) Development Mode - Only PostgreSQL in Docker, run app locally"
echo ""
read -p "Enter choice (1 or 2): " choice

case $choice in
    1)
        echo ""
        echo "🐳 Starting Full Stack with Docker Compose..."
        echo ""
        docker compose up -d
        echo ""
        echo "✅ Services started!"
        echo ""
        echo "📊 Checking service status..."
        sleep 5
        docker compose ps
        echo ""
        echo "📝 View logs with: docker compose logs -f"
        echo "🌐 Application URL: http://localhost:8080"
        echo "💚 Health Check: http://localhost:8080/actuator/health"
        echo ""
        echo "🛑 Stop services with: docker compose down"
        ;;
    2)
        echo ""
        echo "🐳 Starting PostgreSQL only..."
        echo ""
        docker compose -f docker-compose.dev.yml up -d
        echo ""
        echo "✅ PostgreSQL started!"
        echo ""
        echo "Now run the Java application:"
        echo "  - From IDE with VM options: -Xms512m -Xmx2048m"
        echo "  - Or run: ./run.sh"
        echo ""
        ;;
    *)
        echo "❌ Invalid choice"
        exit 1
        ;;
esac
