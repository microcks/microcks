#!/bin/bash
set -e

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

#  Add the Microcks Helm repository and update it.
echo "[INFO] Adding Microcks Helm repository..."
helm repo add microcks https://microcks.io/helm
helm repo update

# Install Microcks using Helm with dynamic nip.io URLs based on the minikube IP.
echo "[INFO] Installing Microcks..."
helm install microcks ./microcks --namespace microcks \
   --set microcks.url=microcks.$(minikube ip).nip.io \
   --set keycloak.url=keycloak.$(minikube ip).nip.io \
   --set keycloak.privateUrl=http://microcks-keycloak.microcks.svc.cluster.local:8080


# Wait for the Microcks pods to become ready.
echo "[INFO] Waiting for Microcks pods to be ready (timeout: 300s)..."
kubectl wait --for=condition=Ready pod -n $NAMESPACE -l app=microcks --timeout=300s

echo "------------------------------------------------------"
echo "Microcks installation is complete!"
echo "Microcks is available at: https://microcks.${MINIKUBE_IP}.nip.io"
echo "Keycloak is available at: https://keycloak.${MINIKUBE_IP}.nip.io"
echo "------------------------------------------------------"
