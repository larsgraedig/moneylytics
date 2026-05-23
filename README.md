# Moneylytics

## Local development

### Backend

Two Spring profiles are available:

#### Default profile — persistent Postgres

Requires Docker. Data survives application and container restarts.

```bash
docker compose up -d       # start Postgres
./gradlew :web:bootRun     # start the backend
```

To stop Postgres without losing data:

```bash
docker compose stop
```

To wipe all data and start fresh:

```bash
docker compose down -v
```

Data is stored in a Docker-managed named volume. To find its location on the physical filesystem:

```bash
docker volume inspect moneylytics_postgres_data
```

#### `local` profile — ephemeral H2 with dummy data

No Docker needed. Uses an in-memory H2 database that is pre-seeded with ~300 dummy
transactions on every startup. All data is lost when the application stops.

```bash
./gradlew :web:bootRun --args='--spring.profiles.active=local'
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
| http://localhost:8082 | H2 database console (`local` profile only) |

#### H2 console connection (`local` profile only)

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:moneylyticsdb` |
| Username | `sa` |
| Password | *(leave empty)* |
