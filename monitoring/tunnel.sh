#!/usr/bin/env bash
# Forwards the VPS metrics ports to this laptop. Keep it running while you watch Grafana.
# Usage: monitoring/tunnel.sh user@vps-host
set -euo pipefail
exec ssh -N \
  -L 18080:localhost:8080 \
  -L 19100:localhost:9100 \
  -L 18081:localhost:8081 \
  -L 19187:localhost:9187 \
  "${1:?usage: $0 user@vps-host}"
