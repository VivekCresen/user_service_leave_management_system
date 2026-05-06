# Leave Management System - Monitoring Stack

Complete monitoring solution with Prometheus, Grafana, and RabbitMQ for the Leave Management System microservices.

## Quick Start

```bash
cd back-end/user_service_leave_management_system/monitoring
./start.sh
```

Then open Grafana at http://localhost:3001 (login: admin/admin123)

## What's Included

- **Prometheus**: Metrics collection and storage
- **Grafana**: Visualization and dashboards
- **RabbitMQ**: Message broker with Prometheus metrics

## Services

| Service | Port | URL | Credentials |
|---------|------|-----|-------------|
| Grafana | 3001 | http://localhost:3001 | admin / admin123 |
| Prometheus | 9090 | http://localhost:9090 | - |
| RabbitMQ Management | 15672 | http://localhost:15672 | guest / guest |
| RabbitMQ AMQP | 5672 | - | guest / guest |
| RabbitMQ Metrics | 15692 | http://localhost:15692/metrics | - |

## Monitored Applications

The Prometheus configuration scrapes metrics from:

- **User Service** (port 8081)
- **Leave Service** (port 8082)
- **API Gateway** (port 8080)
- **Discovery Service** (port 8761)
- **RabbitMQ** (port 15692)

## Dashboards

Two pre-configured dashboards are automatically provisioned:

1. **Spring Boot Monitoring**
   - HTTP request rate
   - Average response time
   - CPU usage
   - JVM memory usage
   - Service status

2. **RabbitMQ Monitoring**
   - Queue messages
   - Message publish rate
   - Message delivery rate
   - Active connections

## Manual Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# View logs
docker compose logs -f

# View specific service logs
docker compose logs -f grafana
docker compose logs -f prometheus
docker compose logs -f rabbitmq

# Restart a service
docker compose restart grafana

# Remove all data (clean slate)
docker compose down -v
```

## Troubleshooting

### Dashboards show "No Data"

1. Make sure your Spring Boot applications are running
2. Check if Prometheus can reach them:
   - Open http://localhost:9090/targets
   - All targets should show "UP" status
3. If targets are down, verify:
   - Applications are running on the correct ports
   - Actuator endpoints are exposed
   - No firewall blocking connections

### Grafana shows datasource errors

Restart Grafana:
```bash
docker compose restart grafana
```

### RabbitMQ metrics not showing

1. Check RabbitMQ plugins are enabled:
```bash
docker exec rabbitmq-leave-management rabbitmq-plugins list
```

2. Verify Prometheus plugin is enabled:
```bash
curl http://localhost:15692/metrics
```

## Configuration Files

- `docker-compose.yml` - Container orchestration
- `prometheus/prometheus.yml` - Prometheus scrape configuration
- `grafana/provisioning/datasources/datasources.yml` - Grafana datasource
- `grafana/provisioning/dashboards/dashboards.yml` - Dashboard provisioning
- `grafana/dashboards/*.json` - Dashboard definitions
- `rabbitmq/enabled_plugins` - RabbitMQ plugins

## Data Persistence

All data is stored in Docker volumes:
- `prometheus-data` - Prometheus time-series data
- `grafana-data` - Grafana dashboards and settings
- `rabbitmq-data` - RabbitMQ queues and messages

To remove all data:
```bash
docker compose down -v
```

## Network

All services run on the `monitoring-network` bridge network, allowing them to communicate using service names (e.g., `http://prometheus:9090`).

## Next Steps

1. Start the monitoring stack: `./start.sh`
2. Start your Spring Boot applications
3. Open Grafana and explore the dashboards
4. Customize dashboards as needed
5. Set up alerts (optional)

## Support

For issues or questions, check:
- Prometheus targets: http://localhost:9090/targets
- Grafana datasources: http://localhost:3001/datasources
- Container logs: `docker compose logs`
