# Moneylytics

## Local development

### Backend

Two Spring profiles are available:

#### Default profile — persistent Postgres

Requires Docker. Data survives application and container restarts.

```bash
make run
```

This starts Postgres if it isn't already running, waits until it is ready, then starts the backend. Equivalent to:

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
make reset-db
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

### Docker (full stack)

To run the frontend, backend, and Postgres all in Docker:

```bash
make compose
```

This builds the backend image to the local Docker daemon via Jib, then starts all three services. Equivalent to:

```bash
./gradlew :web:jibDockerBuild   # build backend image locally
docker compose up -d            # start all services
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |

### Importing transactions

With the backend running, import a CSV file using the bundled script:

```bash
./import-transactions.sh path/to/export.csv
```

The format is detected automatically from the header row. Supported formats:

| Format | Key columns |
|--------|-------------|
| MLP Banking | `Kategorie`, `Unterkategorie`, `Buchungstag`, `Valutadatum`, `Betrag`, `EUR`, `IBAN Auftragskonto` |
| Standard | `Main category`, `Second Category`, `booking_date`, `valuta_date`, `amount`, `currency`, `account_type` |

Re-importing the same file is safe — duplicate transactions are detected by a SHA-256 fingerprint and silently skipped.

To point at a different backend:

```bash
BASE_URL=https://api.example.com ./import-transactions.sh export.csv
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

## Deployment

Builds and pushes both the backend (via Jib) and frontend images to Docker Hub, then performs a Helm upgrade into the target Kubernetes namespace.

```bash
make release            # deploy to moneylytics-dev (default)
make release ENV=prod   # deploy to moneylytics-prod
```

Requires `DOCKERHUB_USERNAME` and `DOCKERHUB_PASSWORD` environment variables to be set.

#### H2 console connection (`local` profile only)

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:moneylyticsdb` |
| Username | `sa` |
| Password | *(leave empty)* |
