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

## Alert testen (Probe)

Fake-ERROR-Log direkt in Loki pushen, ohne einen echten Fehler zu provozieren:

```bash
# Terminal 1 — Port-Forward offen lassen
kubectl port-forward -n monitoring svc/loki-gateway 3100:80 --context moneylytics

# Terminal 2 — Fake-Error pushen
curl -X POST http://localhost:3100/loki/api/v1/push \
  -H "Content-Type: application/json" \
  -d "{
    \"streams\": [{
      \"stream\": {
        \"service_name\": \"api\",
        \"container\": \"moneylytics\",
        \"log_level\": \"ERROR\",
        \"namespace\": \"moneylytics\"
      },
      \"values\": [[
        \"$(python3 -c 'import time; print(int(time.time() * 1e9))')\",
        \"{\\\"message\\\":\\\"Probe-Alert: simulierter Fehler\\\",\\\"log\\\":{\\\"level\\\":\\\"ERROR\\\"}}\"
      ]]
    }]
  }"
```

Der Log ist sofort in Grafana → Explore → Loki sichtbar. Der Alert **Backend Error Log** wechselt innerhalb von 5 Minuten auf **Firing** und nach weiteren 5 Minuten ohne neue Errors zurück auf **Normal**.

## DNS

`grafana.moneylytics.io` must point to the same IP as `app.moneylytics.io` (Traefik LoadBalancer).
