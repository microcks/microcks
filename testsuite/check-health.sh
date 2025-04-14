#!/bin/bash

set -euo pipefail

METHOD="docker"

usage() {
  cat <<EOF
Usage: $0 [--method docker|podman|helm]
  --method    Which backend to check.
              docker/podman: container healthchecks
              helm:          check Deployments in the 'microcks' namespace
EOF
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
  echo "Error: Invalid method '$METHOD'. Use docker, podman or helm."
  usage
fi

INSPECT_CMD="$METHOD"

unhealthy=()

if [[ "$METHOD" == "docker" || "$METHOD" == "podman" ]]; then
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

else
  mapfile -t deps < <(
    kubectl -n microcks get deploy \
      -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'
  )

  if [[ ${#deps[@]} -eq 0 ]]; then
    echo "No Deployments found in namespace 'microcks'"
    unhealthy+=("microcks:no-deployments")
  else
    for d in "${deps[@]}"; do
      status_line=$(kubectl -n microcks get deploy "$d" \
        -o jsonpath='{.spec.replicas}{" "}{.status.availableReplicas}{"\n"}' 2>/dev/null) \
        || status_line=""

      if [[ -z "$status_line" ]]; then
        echo "Could not fetch status for $d"
        unhealthy+=("microcks/$d:fetch-failed")
        continue
      fi

      # split into desired & available
      desired=${status_line%% *}
      available=${status_line##* }
      available=${available:-0}

      echo "- $d: $available/$desired available"

      if (( available < desired )); then
        unhealthy+=("microcks/$d:$available/$desired")
      fi
    done
  fi
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
