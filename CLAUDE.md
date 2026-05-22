# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build / test
./gradlew build
./gradlew :web:test

# Lint
./gradlew ktlintCheck    # style check
./gradlew ktlintFormat   # auto-fix style
./gradlew detekt         # static analysis

# Run locally
./gradlew :web:bootRun

# Deploy (requires DOCKERHUB_USERNAME, DOCKERHUB_PASSWORD env vars)
make publish   # build + push image (image tag = COMMIT_HASH)
make release   # publish + helm upgrade
```

## Architecture

The `web` module (the only active subproject — `ingester/` is a placeholder) uses **hexagonal architecture** with these
layers:

- `domain/` — Pure domain models, no framework dependencies. `Transaction` is a plain data class with no JPA
  annotations.
- `application/port/input/` — Use case interfaces (e.g., `ImportTransactionsUseCase`)
- `application/port/output/` — Repository interfaces (e.g., `TransactionRepository`)
- `application/service/` — Use case implementations; depend only on domain + output ports
- `adapter/input/web/` — REST controllers, CSV parser, request/response types
- `adapter/output/persistence/` — JPA entities and repository implementations

Dependencies flow inward only: adapters → application → domain. Directories are named `input`/`output` rather than `in`/
`out` because `in` is a reserved keyword in Kotlin.

**Current feature — CSV import flow:** `POST /transactions/import` (multipart) → `TransactionImportController` →
`CsvTransactionParser` (returns sealed `CsvParseResult`) → `ImportTransactionsUseCase` → `TransactionImportService` →
`TransactionRepository` port → `TransactionPersistenceAdapter` → JPA → H2

## CSV Format

The CSV uses German locale. Non-obvious details for `CsvTransactionParser`:

- Dates: `dd.MM.yyyy`
- Amounts: `.` is the thousands separator, `,` is the decimal separator (e.g., `-1.234,56` → `BigDecimal("-1234.56")`)
- Required columns: `Kategorie`, `Unterkategorie`, `Buchungstag`, `Valutadatum`, `Betrag`, `EUR`
- Parser collects **all** validation errors before returning (not fail-fast)

Sample CSV with 98 transactions: `web/src/main/resources/AccountSheet.csv` (used in tests)

## Coding Standards

- **Tests**: AssertJ for assertions, Mockito Kotlin for mocking (**no MockK**), Arrange-Act-Assert pattern, backtick
  names: `` `should ... when ...` ``
- **Data classes** must be immutable (`DataClassShouldBeImmutable` enforced by Detekt)
- **Logging**: `private val logger = KotlinLogging.logger {}`
- Use trailing commas in multi-line collections/function calls and named arguments
- All comments in English
- Prefer suspending functions and coroutine APIs over blocking calls (WebFlux stack)
