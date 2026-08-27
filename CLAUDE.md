# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build / test
./gradlew build
./gradlew :web:test
./gradlew :web:test --tests "com.moneylytics.api.SomeTest"        # single test class
./gradlew :web:test --tests "com.moneylytics.api.SomeTest.method" # single test method

# Lint / static analysis
./gradlew ktlintCheck    # style check
./gradlew ktlintFormat   # auto-fix style
./gradlew detekt         # static analysis

# Run locally (default profile — requires Postgres via Docker)
docker compose up -d
./gradlew :web:bootRun

# Run locally with H2 in-memory + dummy data (no Docker needed)
./gradlew :web:bootRun --args='--spring.profiles.active=local'

# Frontend dev server (proxies API calls to localhost:8080)
cd frontend && npm run dev

# Deploy (requires DOCKERHUB_USERNAME, DOCKERHUB_PASSWORD env vars)
make publish   # build + push image (image tag = COMMIT_HASH)
make release   # publish + helm upgrade
```

## Architecture

The `web` module (the only active subproject — `ingester/` is a placeholder) uses **hexagonal architecture**:

- `domain/` — Pure Kotlin data classes, no framework dependencies
- `application/port/input/` — Use case interfaces and command/query objects
- `application/port/output/` — Repository and classifier interfaces
- `application/service/` — Use case implementations; depend only on domain + output ports
- `adapter/input/web/` — WebFlux REST controllers, CSV/CAMT parsers, request/response DTOs
- `adapter/output/persistence/` — JPA entities, Spring Data repositories, persistence adapters

Dependencies flow inward only: adapters → application → domain. Directories are named `input`/`output` rather than `in`/`out` because `in` is a reserved keyword in Kotlin.

**Auth pattern:** Every controller receives `@AuthenticationPrincipal principal: UserDetails` and calls `resolveUserUseCase.resolveUser(principal.username)` to obtain the internal `userId: Long`. The `local` profile bypasses OAuth entirely (seeds dummy data via `LocalDataInitializer`).

**Frontend:** React + TypeScript, served by Vite (`frontend/`). All API calls go through `src/api/client.ts` (`fetchWithUser`). Vite proxies `/transactions`, `/accounts`, `/categories`, `/users`, `/thresholds`, `/budgets`, `/collections`, `/auth`, `/oauth2` to `localhost:8080`. Styles live in a single `src/index.css`. Translations in `src/i18n/locales/de.json` and `en.json`.

## CSV Format

The CSV uses German locale. Non-obvious details for `CsvTransactionParser`:

- Dates: `dd.MM.yyyy`
- Amounts: `.` is the thousands separator, `,` is the decimal separator (e.g., `-1.234,56` → `BigDecimal("-1234.56")`)
- Required columns: `Kategorie`, `Unterkategorie`, `Buchungstag`, `Valutadatum`, `Betrag`, `EUR`
- Parser collects **all** validation errors before returning (not fail-fast)

## Coding Standards

- **Tests**: AssertJ for assertions, Mockito Kotlin for mocking (**no MockK**), Arrange-Act-Assert pattern, backtick names: `` `should ... when ...` ``
- Controller unit tests instantiate the controller directly with mocked use cases — no Spring context needed; use `runTest` for coroutine suspension
- Integration tests extend `AbstractJpaRepositoryIT` (persistence layer) or `AbstractServiceIT` (service + persistence), are suffixed with `IT`, and live in `src/test/kotlin`
- **Data classes** must be immutable (`DataClassShouldBeImmutable` enforced by Detekt)
- **Logging**: `private val logger = KotlinLogging.logger {}`
- Use trailing commas in multi-line collections/function calls and named arguments
- All comments in English
- Prefer suspending functions and coroutine APIs over blocking calls (WebFlux stack)
- Put business logic into the backend as much as possible; the frontend should be a thin client (React + TypeScript)
- Do not use Kotlin Double Bangs (`!!`)
- Whenever new code is added it should be covered by unit tests (and integration tests if applicable). If a new feature is added, it should be covered by an integration test.

## Procedure

- Always run `ktlintFormat` after adding new Kotlin files, then verify with `ktlintCheck` and `detekt` before finishing any code changes. Fix any detekt violations — do not leave the build in a failing state.
- Liquibase changesets are numbered sequentially (`0001`, `0002`, …). Check the highest existing number in `web/src/main/resources/db/changelog/changes/` before creating a new one.
- New changesets are individual YAML files (`changes/XXXX-name.yaml`), referenced from `db.changelog-master.yaml` via `include`. Prefer native Liquibase change types (e.g. `renameTable`, `addColumn`) over raw `sql` blocks — they auto-generate rollback. When raw SQL is unavoidable, use a single `sql` block with `splitStatements: true` and a YAML block scalar (`|`) for multiple statements; add an explicit `rollback` block. Raw SQL scripts referenced by YAML changelogs live in `changes/scripts/`. Use `constraints.checkConstraint` inline on the column (OSS) instead of `addCheckConstraint` (Liquibase Pro only).
- **Database table names are singular** (e.g. `account`, `transaction`, `organization`). Use `@Table(name = "singular_name")` in JPA entities. Note: `user` is a reserved word in PostgreSQL — quote it with backticks in the annotation: `@Table(name = "\`user\`")`.
