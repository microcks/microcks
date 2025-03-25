#!/bin/bash

usage() {
    echo "Usage: $0 [--mode {default|proxy|devmode}] [--addons addon1,addon2,...]"
    exit 1
}

MODE="default"
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
        *)
            usage
            ;;
    esac
    shift
done

# Get the directory where this script is located.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || { echo "Failed to change directory to ${SCRIPT_DIR}"; exit 1; }

# Determine main docker-compose file based on the mode
case $MODE in
    default)
        MAIN_COMPOSE="docker-compose.yml"
        ;;
    proxy)
        MAIN_COMPOSE="docker-compose-with-proxy.yml"
        ;;
    dev)
        MAIN_COMPOSE="docker-compose-devmode.yml"
        ;;
    *)
        echo "Error: Invalid mode. Choose from default, proxy, or devmode."
        usage
        ;;
esac

# Start building the command with the main compose file
CMD="docker-compose -f docker-compose/${MAIN_COMPOSE}"

for addon in "${ADDONS[@]}"; do
    addon=$(echo "$addon" | xargs)  # trim whitespace
    ADDON_FILE="docker-compose/${addon}.yml"
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
    echo "Error: Docker Compose encountered an error, installation aborted."
    exit 1
fi
