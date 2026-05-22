# Moneylytics

## Local development

### Backend

```bash
./gradlew :web:bootRun
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs at **http://localhost:5173** and proxies `/transactions` to the backend on port 8080.

### URLs

| URL | What it does |
|-----|--------------|
| http://localhost:5173 | Sankey chart frontend |
| http://localhost:8080/swagger-ui.html | Redirects → Swagger UI |
| http://localhost:8080/swagger-ui/index.html | Full interactive Swagger UI |
| http://localhost:8080/v3/api-docs | Raw OpenAPI JSON spec |
| http://localhost:8082 | H2 database console |

#### H2 console connection

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:moneylyticsdb` |
| Username | `sa` |
| Password | *(leave empty)* |
