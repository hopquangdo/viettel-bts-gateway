# Viettel API Gateway

Spring Cloud Gateway dung lam cong vao duy nhat cho cac service Viettel.

## Chay local

```powershell
Copy-Item .env.example .env
.\gradlew.bat test
.\gradlew.bat bootRun
```

Gateway mac dinh chay tai `http://localhost:8090`, route API ve `GATEWAY_BACKEND_URL`.
`GATEWAY_AI_ASSISTANT_URL` van tro ve backend trong pha 1 de giu nguyen hop dong chat cu.

## Kubernetes

1. Tao Secret tu `deploy/k8s/secret.example.yaml`, thay `JWT_SECRET` bang gia tri that.
2. Doi host va image trong `deploy/k8s/` theo moi truong.
3. Render/apply tu repo root: `kubectl apply -k deploy/k8s/`.

Ingress da tat buffering va dat timeout dai cho SSE. Khong commit Secret that.