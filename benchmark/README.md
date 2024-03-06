# K6 loadtesting on Microcks mock services

1. Start you Microck instance 
2. Import MicrocksIO Samples APIs / Pastry API - 2.0
3. Import MicrocksIO Samples APIs / Movie Graph API
4. Import MicrocksIO Samples APIs / HelloService Mock
5. Import any other mock if you want to have more APIs to browse

## env vars

```
export MICROCKS_BASE_URL=http://172.31.243.54:8080
export PROMETHEUS_RW_URL=http://172.31.243.54:9080/api/v1/write
export K6_VERSION=0.48.0
```

If you're running Microcks locally (via docker-compose or other), you may use thes ones:

```
export MICROCKS_BASE_URL=http://host.docker.internal:8080
export K6_VERSION=0.48.0
```

## Simple script execution

```
docker run --rm -i \
    -e BASE_URL=${MICROCKS_BASE_URL} \
    grafana/k6:${K6_VERSION} \
    run \
    - < bench-microcks.js
```

## Override number of virtual users and durations

```
docker run --rm -i \
    -e BASE_URL=${MICROCKS_BASE_URL} \
    grafana/k6:${K6_VERSION} run  \
    --duration 5s \
    --vus 100 \
    - < bench-microcks.js
```

## Export metrics to prometheus endpoint while override number of virtual users and durations

```
docker run --rm -i \
    -e K6_PROMETHEUS_RW_SERVER_URL=${PROMETHEUS_RW_URL} \
    -e K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),min,max" \
    -e BASE_URL=${MICROCKS_BASE_URL} \
    grafana/k6:${K6_VERSION} run  \
    --duration 60s \
    --vus 400 \
    --tag testid=$(date -u +"%Y-%m-%dT%H:%M:%SZ") \
    -o experimental-prometheus-rw \
    - < bench-microcks.js
```
