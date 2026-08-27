# Monitoring Stack

Grafana observability stack: Logs (Loki) + Metrics (Prometheus) + Traces (Tempo).

## Prerequisites

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

kubectl create namespace monitoring

kubectl create secret generic grafana-admin \
  -n monitoring \
  --from-literal=admin-user=admin \
  --from-literal=admin-password='<SECURE_PASSWORD>'
```

## Deploy / Update

```bash
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring -f deployment/monitoring/kube-prometheus-stack-values.yaml --timeout 10m

helm upgrade --install loki grafana/loki \
  -n monitoring -f deployment/monitoring/loki-values.yaml

helm upgrade --install promtail grafana/promtail \
  -n monitoring -f deployment/monitoring/promtail-values.yaml

helm upgrade --install tempo grafana/tempo \
  -n monitoring -f deployment/monitoring/tempo-values.yaml

helm upgrade --install moneylytics-dashboards ./deployment/monitoring/dashboards \
  -n monitoring

kubectl apply -f deployment/monitoring/alerts/
```

Via GitHub Actions: trigger **Deploy Monitoring Stack** workflow manually.

## Adding Dashboards

1. Export dashboard JSON from Grafana UI (Share → Export → Save to file)
2. Place JSON file in `deployment/monitoring/dashboards/files/`
3. Run `helm upgrade --install moneylytics-dashboards ./deployment/monitoring/dashboards -n monitoring`

## DNS

`grafana.moneylytics.io` must point to the same IP as `app.moneylytics.io` (Traefik LoadBalancer).
