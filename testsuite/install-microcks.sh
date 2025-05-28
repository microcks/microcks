#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [--image <image-name>] [--tag <image-tag>]

Options:
  --image   Microcks image to run (default: microcks-uber)
            e.g. microcks-uber, microcks-uber-async-minion, etc.

  --tag     Image tag to use (default: nightly)
            e.g. nightly, nightly-native, <any valid tag>

Note: Supported images and tags can be found at:
      https://quay.io/organization/microcks
EOF
  exit 1
}

error_exit() {
  echo "Error: $1" >&2
  exit "${2:-1}"
}

# Defaults
IMAGE="microcks-uber"
TAG="nightly"

# Parse flags
while [[ $# -gt 0 ]]; do
  case $1 in
    --image)
      IMAGE="$2"; shift ;;
    --tag)
      TAG="$2";   shift ;;
    *)
      usage ;;
  esac
  shift
done

FULL_IMAGE="quay.io/microcks/${IMAGE}:${TAG}"

pull_image() {
  echo "Pulling image ${FULL_IMAGE}..."
  if ! docker pull "${FULL_IMAGE}"; then
    error_exit "Failed to pull image ${FULL_IMAGE}"
  fi
}

run_container() {
  echo "Starting container in detached mode (host:8585 → container:8080)..."
  if ! docker run -d --rm -p 8585:8080 "${FULL_IMAGE}"; then
    error_exit "Failed to start container from image ${FULL_IMAGE}"
  fi
}

pull_image
run_container

echo "Microcks is up at http://localhost:8585 (image=${IMAGE}, tag=${TAG})"
