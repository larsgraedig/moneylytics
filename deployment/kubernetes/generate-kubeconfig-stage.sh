#!/usr/bin/env bash
# Run once as a cluster admin against the "moneylytics" context.
# Outputs a base64-encoded kubeconfig to stdout; pipe or copy as KUBE_CONFIG_STAGE GitHub secret.
set -euo pipefail

CONTEXT="moneylytics-stage"
NAMESPACE="moneylytics-stage"
SA="github-actions"
SECRET_NAME="github-actions-token"

echo "Extracting kubeconfig for ServiceAccount '${SA}' in namespace '${NAMESPACE}'..." >&2
echo "Using context: moneylytics (cluster admin)" >&2

SERVER=$(kubectl config view \
  --context "moneylytics" \
  --minify \
  --output jsonpath='{.clusters[0].cluster.server}')

CA_DATA=$(kubectl get secret "${SECRET_NAME}" \
  --context "moneylytics" \
  --namespace "${NAMESPACE}" \
  --output jsonpath='{.data.ca\.crt}')

TOKEN=$(kubectl get secret "${SECRET_NAME}" \
  --context "moneylytics" \
  --namespace "${NAMESPACE}" \
  --output jsonpath='{.data.token}' | base64 --decode)

KUBECONFIG_YAML=$(cat <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: ${CONTEXT}
    cluster:
      server: ${SERVER}
      certificate-authority-data: ${CA_DATA}
contexts:
  - name: ${CONTEXT}
    context:
      cluster: ${CONTEXT}
      user: ${SA}
      namespace: ${NAMESPACE}
current-context: ${CONTEXT}
users:
  - name: ${SA}
    user:
      token: ${TOKEN}
EOF
)

echo "" >&2
echo "=== Paste the output below as GitHub secret KUBE_CONFIG_STAGE ===" >&2
echo "" >&2
printf '%s' "${KUBECONFIG_YAML}" | base64
echo "" >&2
