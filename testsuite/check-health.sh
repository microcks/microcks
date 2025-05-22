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
    retries=$($INSPECT_CMD inspect -f '{{.Config.Healthcheck.Retries}}' "$cname")

    echo "Interval=${interval}, timeout=${timeout}, start-period=${start_period}, retries=${retries}"

    try=0
    elapsed=0
    sleep $start_period
    timeout_val=$(to_seconds "$timeout")
    interval_val=$(to_seconds "$interval")
    while [[ $elapsed -lt $timeout_val ]] || [[ $try -lt $retries ]]; do
      status=$($INSPECT_CMD inspect -f '{{.State.Health.Status}}' "$cname")
      echo "Status after ${elapsed}s: $status"

      if [[ "$status" == "healthy" ]]; then
        echo "$cname is healthy!"
        break
      fi

      sleep "$interval"
      try=$((try + 1))
      elapsed=$((elapsed + interval_val))
    done

    if [[ "$status" != "healthy" ]]; then
      unhealthy+=("$cname:$status")
    fi
  done
else
  echo "Checking Helm in namespace '$NAMESPACE'"

  mapfile -t deployments < <(
    kubectl get deployments -n "$NAMESPACE" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
    | sort -u | grep -v '^microcks-async-minion$'
  )
  # Append async one at the end if it exists
  if kubectl get deployment microcks-async-minion -n "$NAMESPACE" &>/dev/null; then
    deployments+=("microcks-async-minion")
  fi

  if [[ ${#deployments[@]} -eq 0 ]]; then
    echo "Error: No deployments found in namespace '$NAMESPACE'"
    exit 1
  fi

  for dep in "${deployments[@]}"; do
    echo "Waiting for deployment '$dep' to roll out (timeout: 60s)..."
    retries=3
    attempt=1
    success=false
    while [[ $attempt -le $retries ]]; do
      if kubectl rollout status deployment/"$dep" -n "$NAMESPACE" --timeout=60s; then
        success=true
        break
      else
        echo "Attempt $attempt for deployment '$dep' failed."
        ((attempt++))
        if [[ $attempt -le $retries ]]; then
          echo "Retrying in 5 seconds..."
          sleep 5
        fi
      fi
    done

    if ! $success; then
      echo "Deployment '$dep' failed to roll out after $retries attempts."
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
