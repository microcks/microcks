#!/bin/bash

set -e

METHOD="docker"
NAMESPACE="microcks"

usage() {
  echo "Usage: $0 [--method docker|podman|helm]"
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

if [[ "$METHOD" != "docker" && "$METHOD" != "podman" && "$METHOD" != "helm" ]]; then
  echo "Error: Invalid method '$METHOD'. Use 'docker','podman' or 'helm."
  usage
fi

if [[ "$METHOD" == "docker" || "$METHOD" == "podman" ]]; then
  INSPECT_CMD="$METHOD"

  unhealthy=()

  to_seconds() {
    local value="$1"
    echo "${value//[a-zA-Z]/}"
  }

  containers=($($INSPECT_CMD ps --format '{{.Names}}'))

  if [[ ${#containers[@]} -eq 0 ]]; then
    echo "Error: No containers found"
    exit 1
  fi

  for cname in "${containers[@]}"; do
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
else
  echo "Checking Helm in namespace '$NAMESPACE'"

  mapfile -t deployments < <(
    kubectl get deployments \
      -n "$NAMESPACE" \
      -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'
  )

  if [[ ${#deployments[@]} -eq 0 ]]; then
    echo "Error: No deployments found in namespace '$NAMESPACE'"
    exit 1
  fi

  for dep in "${deployments[@]}"; do
    echo "Waiting for deployment '$dep' to roll out (timeout: 60s)..."
    if ! kubectl rollout status deployment/"$dep" \
         -n "$NAMESPACE" \
         --timeout=60s; then
      echo "Deployment '$dep' failed to roll out in 60s"
      unhealthy+=("$dep")
    fi
  done
fi

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
