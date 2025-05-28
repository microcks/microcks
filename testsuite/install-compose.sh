#!/bin/bash

usage() {
  echo "Usage: $0 [--method {docker|podman}] [--mode {default|proxy|devmode}] [--addons addon1,addon2,...]"
  exit 1
}

MODE="default"
METHOD="docker"
ADDONS=()

# Parse command-line arguments
while [[ "$#" -gt 0 ]]; do
  case $1 in
    --mode)
      MODE="$2"
      shift
      ;;
    --addons)
      IFS=',' read -r -a ADDONS <<< "$2"
      shift
      ;;
    --method)
      METHOD="$2"
      shift
      ;;
    *)
      usage
      ;;
  esac
  shift
done

# Validate method flag
if [[ "$METHOD" != "docker" && "$METHOD" != "podman" ]]; then
  echo "Error: Invalid method. Choose 'docker' or 'podman'."
  usage
fi

# Append -compose to form the command name (e.g. docker-compose or podman-compose)
METHOD+="-compose"

# Get the directory where this script is located.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../install/${METHOD}" || { echo "Failed to change directory to ${SCRIPT_DIR}"; exit 1; }

# Validate that proxy mode is not allowed when using podman
if [[ "$METHOD" == "podman-compose" && "$MODE" == "proxy" ]]; then
  echo "Error: Proxy mode is not supported when using podman."
  exit 1
fi

# Determine main compose file based on the mode
case $MODE in
  default)
    MAIN_COMPOSE="${METHOD}.yml"
    ;;
  proxy)
    MAIN_COMPOSE="${METHOD}-with-proxy.yml"
    ;;
  devmode)
    MAIN_COMPOSE="${METHOD}-devmode.yml"
    ;;
  *)
    echo "Error: Invalid mode. Choose from default, proxy, or devmode."
    usage
    ;;
esac

# Start building the command with the main compose file
CMD="${METHOD} -f ${MAIN_COMPOSE}"

for addon in "${ADDONS[@]}"; do
  addon=$(echo "$addon" | xargs)  # trim whitespace
  ADDON_FILE="${addon}-addon.yml"
  if [[ -f "$ADDON_FILE" ]]; then
    CMD+=" -f ${ADDON_FILE}"
  else
    echo "Error: ${ADDON_FILE} not found, installation aborted."
    exit 1
  fi
done

CMD+=" up -d"

echo "Running command: ${CMD}"

if eval ${CMD}; then
  echo "Microcks installation complete."
else
  echo "Error: ${METHOD} encountered an error, installation aborted."
  exit 1
fi
