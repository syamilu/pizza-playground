# Monitoring

Watch the VPS while a load test runs. The agents run on the server and Prometheus and Grafana run on your laptop. Nothing is collected while the laptop is off, so this is for live sessions, not 24/7 history.

```
VPS (127.0.0.1 only)                 laptop
  api /actuator/prometheus :8080 ─┐
  node-exporter            :9100 ─┼── ssh tunnel ──► Prometheus :9090 ──► Grafana :3001
  cadvisor                 :8081 ─┤                       ▲
  postgres-exporter        :9187 ─┘                  k6 remote write
```

The exporters live in `docker-compose.prod.yml`, so the normal deploy from `main` installs them. None of these ports go through Caddy.

## Run

```bash
monitoring/tunnel.sh user@your-vps            # terminal 1, keep it open
docker compose -f monitoring/docker-compose.yml up -d
```

- Grafana: http://localhost:3001. Open the **Pizza Playground** dashboard.
- Prometheus: http://localhost:9090. **Status → Targets** should show all four jobs UP.

Stop with `docker compose -f monitoring/docker-compose.yml down`. Add `-v` to also drop the collected data.

## Put k6 on the same dashboard

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg" \
k6 run -o experimental-prometheus-rw script.js
```

The **Load (k6)** row then lines up with the API, server and Postgres rows. For example, p95 rising while the Hikari "waiting" line climbs means the DB pool is the bottleneck, not the CPU.

## More dashboards

The provisioned dashboard is a small overview. For the full community ones, go to **Dashboards → New → Import** and enter an ID:

| ID | Dashboard |
|---|---|
| 1860 | Node Exporter Full |
| 4701 | JVM (Micrometer) |
| 9628 | PostgreSQL Database |
| 19665 | k6 Prometheus |

## Troubleshooting

- **Target DOWN:** is `tunnel.sh` still running? On the VPS, `curl -s localhost:9100/metrics | head` checks the agent itself.
- **Port already in use on the VPS:** change the host side of the port in `docker-compose.prod.yml` and the matching `-L` line in `tunnel.sh`.
- **Empty k6 row:** k6 only writes while a test runs, and the p95 panel needs `K6_PROMETHEUS_RW_TREND_STATS` to include `p(95)`.
