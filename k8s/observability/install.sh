#!/usr/bin/env bash
# Installs Prometheus + Grafana (kube-prometheus-stack) and Tempo into the "observability" namespace,
# then wires up the order-platform scrape config, alert rules and dashboard.
set -euo pipefail
cd "$(dirname "$0")"

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
helm repo update >/dev/null

kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install tempo grafana/tempo -n observability -f tempo-values.yaml
helm upgrade --install kps prometheus-community/kube-prometheus-stack -n observability -f kube-prometheus-stack-values.yaml

kubectl apply -f podmonitor.yaml -f prometheus-rules.yaml

# Dashboard: the Grafana sidecar loads every ConfigMap labelled grafana_dashboard=1
kubectl -n observability create configmap order-platform-dashboard \
  --from-file=order-platform.json=../../docker/grafana/dashboards/order-platform.json \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n observability label configmap order-platform-dashboard grafana_dashboard=1 --overwrite

echo "Grafana: http://localhost:3000 (kind) - admin / admin"
