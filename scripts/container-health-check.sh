#!/bin/bash
set -e

# Function to check container health using Docker's HEALTHCHECK
check_container_health() {
  local container_name=$1
  local timeout=${2:-90}   # Timeout in seconds (default 90)
  local interval=${3:-10}   # Interval between checks (default 10 seconds)
  local elapsed=0

  echo "Checking health for container: ${container_name}..."

  while true; do
    health=$(docker inspect --format='{{json .State.Health.Status}}' "${container_name}")

    if [ "$health" = "\"healthy\"" ]; then
      echo "Container ${container_name} is healthy."
      break
    elif [ "$health" = "\"unhealthy\"" ]; then
      echo "ERROR: Container ${container_name} is unhealthy."
      exit 1
    fi
    echo "Container ${container_name} health status: $health. Waiting..."

    sleep "$interval"
    elapsed=$((elapsed + interval))
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "ERROR: Timed out waiting for container ${container_name} to be healthy."
      exit 1
    fi
  done
}

# List of containers to check. These names should match your docker-compose service names.
containers=("microcks-db" "microcks-postman-runtime" "microcks-sso" "microcks")

for container in "${containers[@]}"; do
  check_container_health "$container" 90 5
done

echo "All containers are healthy."
