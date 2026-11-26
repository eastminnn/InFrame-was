#!/usr/bin/env bash
# 사용법: bash loadtest/run.sh <스크립트> [testid] [k6 옵션...]  예) -e RATE=150
# 결과를 Prometheus 로 보내서 Grafana "k6 부하 테스트" 대시보드에서 본다
set -euo pipefail

script="$1"
testid="${2:-$(basename "$script" .js)-$(date +%H%M%S)}"
shift $(( $# >= 2 ? 2 : 1 ))

K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://localhost:9090/api/v1/write}" \
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max" \
  k6 run -o experimental-prometheus-rw --tag testid="$testid" "$@" "$script"
