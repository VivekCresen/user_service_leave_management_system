#!/bin/bash

echo "Starting Leave Management System Monitoring Stack..."

# Stop any existing containers
docker compose down

# Start the monitoring stack
docker compose up -d

echo "Waiting for services to start..."
sleep 20

# Check service health
echo ""
echo "Checking service health..."
echo "================================"

# Check Prometheus
if curl -s http://localhost:9090/-/healthy > /dev/null; then
    echo "✓ Prometheus is running at http://localhost:9090"
else
    echo "✗ Prometheus is not responding"
fi

# Check Grafana
if curl -s http://localhost:3001/api/health > /dev/null; then
    echo "✓ Grafana is running at http://localhost:3001"
    echo "  Login: admin / admin123"
else
    echo "✗ Grafana is not responding"
fi

# Check RabbitMQ
if curl -s http://localhost:15672 > /dev/null; then
    echo "✓ RabbitMQ Management is running at http://localhost:15672"
    echo "  Login: guest / guest"
else
    echo "✗ RabbitMQ is not responding"
fi

echo ""
echo "================================"
echo "Monitoring stack is ready!"
echo ""
echo "Access points:"
echo "  - Grafana:  http://localhost:3001 (admin/admin123)"
echo "  - Prometheus: http://localhost:9090"
echo "  - RabbitMQ:   http://localhost:15672 (guest/guest)"
echo ""
echo "To view logs: docker compose logs -f"
echo "To stop: docker compose down"
