#!/bin/bash
set -e

# Default value for async flag
ASYNC=false

# Parse command line arguments
while [[ "$#" -gt 0 ]]; do
  case $1 in
    --async) ASYNC=true; shift ;;
    *) echo "Unknown parameter passed: $1"; exit 1 ;;
  esac
done

# Check if minikube is running
MINIKUBE_STATUS=$(minikube status --format '{{.Host}}' 2>/dev/null || true)
if [ "$MINIKUBE_STATUS" != "Running" ]; then
  echo "[ERROR] Minikube is not running. Please start your cluster (e.g., run 'minikube start') and try again."
  exit 1
fi

MINIKUBE_IP=$(minikube ip)
echo "[INFO] Minikube IP is: $MINIKUBE_IP"

# Enable the ingress addon if it's not already enabled.
echo "[INFO] Enabling ingress addon..."
minikube addons enable ingress

# Wait for the ingress controller pod to be ready
echo "Waiting for the ingress controller to be ready..."
kubectl wait --for=condition=Ready pod -n ingress-nginx -l app.kubernetes.io/component=controller --timeout=2m

# Create the microcks namespace if it doesn't exist.
NAMESPACE="microcks"
if ! kubectl get namespace $NAMESPACE >/dev/null 2>&1; then
  echo "[INFO] Creating namespace '$NAMESPACE'..."
  kubectl create namespace $NAMESPACE
fi

# Get the directory where this config is located.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../install/kubernetes" || { echo "Failed to change directory to ${SCRIPT_DIR}"; exit 1; }

#  Use Microcks Helm directly from the sources (not the repo as we have not yet publish changes).
# Install Microcks using Helm with dynamic nip.io URLs based on the minikube IP.
if $ASYNC; then
  echo "[INFO] Adding Strimzi Helm repository..."
  helm repo add strimzi https://strimzi.io/charts/
  echo "[INFO] Installing Strimzi Kafka Operator for async support..."
  helm install strimzi strimzi/strimzi-kafka-operator --namespace microcks
  echo "[INFO] Installing Microcks..."
  helm install microcks ./microcks --namespace=microcks \
      --set appName=microcks --set features.async.enabled=true \
      --set microcks.url=microcks.${MINIKUBE_IP}.nip.io \
      --set keycloak.url=keycloak.${MINIKUBE_IP}.nip.io \
      --set keycloak.privateUrl=http://microcks-keycloak.microcks.svc.cluster.local:8080 \
      --set features.async.kafka.url=kafka.${MINIKUBE_IP}.nip.io
else
  echo "[INFO] Installing Microcks..."
  helm install microcks ./microcks --namespace microcks \
     --set microcks.url=microcks.${MINIKUBE_IP}.nip.io \
     --set keycloak.url=keycloak.${MINIKUBE_IP}.nip.io \
     --set keycloak.privateUrl=http://microcks-keycloak.microcks.svc.cluster.local:8080
fi


# Wait for the Microcks pods to become ready.
echo "[INFO] Waiting for Microcks pods to be ready..."

# Wait for all microcks pods except async-minion
pods=$(kubectl get pods -n "$NAMESPACE" -l app=microcks -o jsonpath='{.items[?(@.metadata.name!="microcks-async-minion")].metadata.name}')

if ! kubectl wait --for=condition=Ready pod -n "$NAMESPACE" $pods --timeout=180s; then
  echo "[WARN] Some Microcks pods (except async-minion) did not become ready within 180s. Continuing anyway."
fi

if $ASYNC; then
  # Wait for async-minion pod last
  if ! kubectl wait --for=condition=Ready pod microcks-async-minion -n "$NAMESPACE" --timeout=120s; then
    echo "[WARN] Async-minion pod did not become ready within timeout. Continuing anyway."
  fi
fi

echo "------------------------------------------------------"
echo "Microcks installation is complete!"
echo "Microcks is available at: https://microcks.${MINIKUBE_IP}.nip.io"
echo "Keycloak is available at: https://keycloak.${MINIKUBE_IP}.nip.io"
echo "------------------------------------------------------"
