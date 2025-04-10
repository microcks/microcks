#!/bin/bash

set -e

METHOD="docker"

usage() {
  echo "Usage: $0 [--method docker|podman]"
  exit 1
}

# Parse flags
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --method)
      METHOD="$2"
      shift 2
      ;;
    *)
      usage
      ;;
  esac
done

if [[ "$METHOD" != "docker" && "$METHOD" != "podman" ]]; then
  echo "Error: Invalid method '$METHOD'. Use 'docker' or 'podman'."
  usage
fi

INSPECT_CMD="$METHOD"

unhealthy=()

to_seconds() {
  local value="$1"
  echo "${value//[a-zA-Z]/}"
}

for cname in $($INSPECT_CMD ps --format '{{.Names}}'); do
  echo "Inspecting container: $cname"

  interval=$($INSPECT_CMD inspect -f '{{.Config.Healthcheck.Interval}}' "$cname")
  timeout=$($INSPECT_CMD inspect -f '{{.Config.Healthcheck.Timeout}}' "$cname")
  start_period=$($INSPECT_CMD inspect -f '{{.Config.Healthcheck.StartPeriod}}' "$cname")

  echo "Interval=${interval}, timeout=${timeout}, start-period=${start_period}"

  elapsed=0
  sleep $start_period
  timeout_val=$(to_seconds "$timeout")
  interval_val=$(to_seconds "$interval")
  while [[ $elapsed -lt $timeout_val ]]; do
    status=$($INSPECT_CMD inspect -f '{{.State.Health.Status}}' "$cname")
    echo "Status after ${elapsed}s: $status"

    if [[ "$status" == "healthy" ]]; then
      echo "$cname is healthy!"
      break
    fi

    sleep "$interval"
    elapsed=$((elapsed + interval_val))
  done

  if [[ "$status" != "healthy" ]]; then
    unhealthy+=("$cname:$status")
  fi
done

if [[ ${#unhealthy[@]} -eq 0 ]]; then
  echo "All containers are healthy!"
  exit 0
else
  echo "Some containers failed health checks:"
  for entry in "${unhealthy[@]}"; do
    echo "$entry"
  done
  exit 1
fi
