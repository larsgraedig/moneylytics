# Plan: Frontend-Businesslogik ins Backend verlagern

## Kontext

CashflowPage und BurnRatePage fetchen aktuell rohe Transaktionslisten und aggregieren
diese vollständig im Browser (Bucketing, Rolling Average, Kumulierung, Runway-Berechnung).
PiePage missbraucht den Sankey-Endpoint und filtert client-seitig Nodes per String-Prefix.
`type=income/expenses` wird an zwei Stellen unabhängig per `amount >= 0` kodiert.
Beim Zuweisen von Transaktionen zu Budgets/Collections wird die „bereits-zugewiesen"-Filterung
client-seitig erledigt.

**Ziel:** Clients enthalten keine Businesslogik mehr. Das Backend ist alleinige Wahrheitsquelle
für Definitionen (Was ist eine Einnahme? Was ist der effektive Betrag?).

Die Trends-Implementierung in `TransactionQueryController.kt` (Zeilen 163–258) ist das
**Referenzmuster**: Fetch → Bucket → Aggregate → typisierte Response.

---

## Phase 1 — Erweiterungen an GET /transactions/list

**Dateien:**
- `adapter/input/web/TransactionQueryController.kt`
- `application/port/input/GetTransactionsUseCase.kt` (`GetTransactionsQuery`)
- `application/service/TransactionQueryService.kt`

### 1a. `type`-Parameter statt `onlyNegative`

Aktuell: `onlyNegative: Boolean` in `GetTransactionsQuery`.
Neu: `type: TransactionType = ALL` mit Enum `ALL | INCOME | EXPENSES`.

```kotlin
enum class TransactionType { ALL, INCOME, EXPENSES }
```

`onlyNegative` in `GetTransactionsQuery` durch `type` ersetzen, alle Aufrufer anpassen.
Im Service filtert dann `amount > 0` / `< 0`.

Frontend-Stellen die entfallen:
- `CashflowPage.tsx:162` — `.filter(tx => type === 'income' ? tx.amount >= 0 : tx.amount < 0)`
- `TransactionsPage.tsx:583` — `filterType === 'income'` / `filterType === 'expenses'`
- `api/transactions.ts`: `fetchAllTransactions` bekommt optionalen `type`-Param

### 1b. `excludeCollectionId` + `excludeBudgetId` Parameter

Neu in `GetTransactionsQuery`:
```kotlin
val excludeCollectionId: Long? = null
val excludeBudgetId: Long? = null
```

Im Service: Filter auf CollectionTransaction- bzw. BudgetTransactionLink-Tabelle.

Frontend-Stellen die entfallen:
- `CollectionsPage.tsx:190` — `assignedIds`-Set + `.filter(tx => !assignedIds.has(tx.id))`
- `BudgetsPage.tsx:455` — analoges Pattern

---

## Phase 2 — Neuer Endpoint: GET /transactions/cashflow

**Neue Datei:** `application/port/input/GetCashflowUseCase.kt`
**Erweiterung:** `application/service/TransactionQueryService.kt`
**Erweiterung:** `adapter/input/web/TransactionQueryController.kt`

### Request
```
GET /transactions/cashflow?from=&to=&granularity=MONTHLY|YEARLY&iban=
```

### Response
```kotlin
data class CashflowResponse(
    val granularity: Granularity,
    val buckets: List<CashflowBucket>,
)

data class CashflowBucket(
    val key: String,                  // z.B. "2025-03" oder "2025"
    val incomeGross: BigDecimal,      // Summe aller amount >= 0
    val incomeNet: BigDecimal,        // Summe aller effectiveAmount >= 0
    val expensesGross: BigDecimal,    // abs(Summe aller amount < 0)
    val expensesNet: BigDecimal,      // abs(Summe aller effectiveAmount < 0)
    val net: BigDecimal,              // incomeNet - expensesNet
)
```

### Backend-Logik
1. Fetch alle Transaktionen (`type=ALL`)
2. Bucket-Key via bestehendem `Granularity`-Enum + `generateBuckets` (aus Trends wiederverwenden)
3. Aggregiere mit `transaction.effectiveAmount()` (bereits im Domain-Model vorhanden)
4. Lücken mit `BigDecimal.ZERO` füllen

Frontend-Entfernung: Aggregations-Block `CashflowPage.tsx:127–151` + `toDisplayBuckets`-Transformation.

---

## Phase 3 — Neuer Endpoint: GET /transactions/burnrate

**Neue Datei:** `application/port/input/GetBurnRateUseCase.kt`
**Erweiterung:** `application/service/TransactionQueryService.kt`
**Erweiterung:** `adapter/input/web/TransactionQueryController.kt`

### Request
```
GET /transactions/burnrate?from=&to=&iban=&rollingWindow=7
```

### Response
```kotlin
data class BurnRateResponse(
    val points: List<BurnRatePoint>,
    val totalExpenses: BigDecimal,
    val totalIncome: BigDecimal,
    val avgPerDay: BigDecimal,
)

data class BurnRatePoint(
    val date: String,
    val expenses: BigDecimal,         // Tagesausgaben (effectiveAmount)
    val rollingAvg: BigDecimal,       // Rolling Average über rollingWindow Tage
    val cumulative: BigDecimal,       // Kumulierte Ausgaben seit `from`
    val cumulativeIncome: BigDecimal, // Kumuliertes Einkommen seit `from`
)
```

### Backend-Logik
1. Fetch Transaktionen (`type=ALL`)
2. Segregiere in Ausgaben (`effectiveAmount < 0`) und Einnahmen (`effectiveAmount > 0`)
3. Aggregiere Ausgaben per Tag → `Map<LocalDate, BigDecimal>`
4. Lücken zwischen `from` und `to` mit `BigDecimal.ZERO` füllen
5. Rolling Average: Schiebendes Fenster über `rollingWindow` Tage
6. Kumulation: laufende Summe über alle Tage

Frontend-Entfernung:
- `BurnRatePage.tsx:57–75` — `buildPoints()`
- `BurnRatePage.tsx:278–343` — Runway, income-Aggregation, Soll-Rate

> **Hinweis:** Projektion (Soll-Kurve für die Zukunft) und Runway-Anzeige bleiben im Frontend,
> da sie interaktionsabhängig sind (Benutzer ändert `rollingWindow` → Frontend interpoliert).
> Diese sind *Darstellung*, keine Businesslogik.

---

## Phase 4 — Neuer Endpoint: GET /transactions/category-totals

**Erweiterung:** `adapter/input/web/TransactionQueryController.kt`

PiePage nutzt aktuell den Sankey-Endpoint und filtert Nodes per `nodeKey.startsWith('cat:')`.
Das überträgt den gesamten Graphen, obwohl nur Kategorie-Summen benötigt werden.

### Request
```
GET /transactions/category-totals?from=&to=&iban=&category=
```
Ohne `category`: Summen pro Hauptkategorie.
Mit `category=X`: Summen pro Unterkategorie von X.

### Response
```kotlin
data class CategoryTotalsResponse(
    val items: List<CategoryTotal>,
)

data class CategoryTotal(
    val name: String,
    val value: BigDecimal,
)
```

Nutzt bestehende `GetTransactionsQuery` mit `type=EXPENSES`, gruppiert nach `category`
oder `subcategory`. Sortierung nach `value` DESC im Backend.

Frontend-Entfernung:
- `PiePage.tsx:44–62` — Sankey-Node-Filterung und Sortierung
- PiePage importiert `fetchCategoryTotals` statt `fetchSankeyData`

---

## Phase 5 — Budget: Pre-calculated Fields

**Erweiterung:** `adapter/input/web/BudgetController.kt` (Response-Typen)
**Erweiterung:** `application/service/BudgetService.kt`

`effectiveContrib(amount, transactionAmount)` ist in `BudgetsPage.tsx:36` und
`BudgetDetail.tsx:20` dupliziert und berechnet den Budget-Beitrag einer Transaktion.

`BudgetResponse` bekommt berechnete Felder:
```kotlin
data class BudgetResponse(
    // ... bestehende Felder ...
    val totalContributions: BigDecimal,        // NEU: Summe aller effectiveContrib
    val chartPoints: List<BudgetChartPoint>,   // NEU: sortierte kumulative Punkte
)

data class BudgetChartPoint(val date: String, val cumulative: BigDecimal)
```

Die `effectiveContrib`-Logik wird private Methode im `BudgetService`.

Frontend-Entfernung:
- `BudgetsPage.tsx:36–39` + `BudgetDetail.tsx:20–23` — `effectiveContrib` weg
- `BudgetDetail.tsx:33–46` — `buildChartData()` weg
- `.reduce()` für Summen in beiden Dateien weg

---

---

## Testdaten-Setup & automatisierte Verifikation

### Teststrategie

Das Projekt verwendet **reine Unit-Tests ohne @SpringBootTest** (Muster aus
`TransactionQueryControllerTest`). Die Verifikation folgt demselben Muster:

- Jede neue Service-Methode bekommt einen dedizierten Unit-Test
- Der Test erstellt **feste, handberechnete Eingabedaten** als `Transaction`-Objekte
- Er assertiert **exakte Ausgabewerte** — keine probabilistischen Checks
- Der `LocalDataInitializer` wird mit deterministischen Zusatzdaten erweitert,
  damit alle Features im Browser sichtbar sind

---

### Erweiterung LocalDataInitializer.kt (Vorstufe zu Phase 1)

Alle folgenden Entitäten werden am Ende von `run()` hinzugefügt, nach den bestehenden
zufälligen Transaktionen. Beträge sind **exakt fest kodiert** (keine Random).

**Datei:** `config/LocalDataInitializer.kt`

#### A) Offset-Links — 2 Transaktionspaare

```
Paar 1 "Arzt/Erstattung" (März 2025):
  TX-A: 2025-03-15, Gesundheit/Arzt,        -€180,00  → effectiveAmount = -€60,00
  TX-B: 2025-03-18, Einnahmen/Erstattung,   +€120,00  → effectiveAmount =  €0,00
  Offset: amountA=120, amountB=120

Paar 2 "Restaurant/Anteil" (Juni 2025):
  TX-A: 2025-06-20, Lebensmittel/Restaurant, -€95,00  → effectiveAmount = -€47,50
  TX-B: 2025-06-21, Einnahmen/Überweisung,  +€47,50   → effectiveAmount =  €0,00
  Offset: amountA=47.50, amountB=47.50
```

#### B) Thresholds

```
Lebensmittel | MONTHLY | notice=400  | warning=600 | critical=800
Transport    | MONTHLY | notice=150  | warning=250 | critical=null
Freizeit     | MONTHLY | notice=80   | warning=null | critical=null
```

#### C) Budgets — 2 Stück mit bekannten Summen

```
"Urlaub 2025" (target=1200):
  Link 1: TX Kategorie=Reise, 2025-04-10, -€380,00, amount=null (voller Betrag)
  Link 2: TX Kategorie=Reise, 2025-05-22, -€490,00, amount=null (voller Betrag)
  Link 3: TX Kategorie=Reise, 2025-07-01, -€200,00, amount=null (voller Betrag)
  → totalContributions = €1.070,00 | progress = 89,2 %

"Neue Küche" (target=3500):
  Link 1: TX Kategorie=Wohnen, 2025-09-05, -€800,00, amount=500 (partiell)
  → totalContributions = €500,00 | progress = 14,3 %
```

#### D) Collections — 2 Stück

```
"Sommer 2025":
  5 Transaktionen aus Juni–August 2025 (Lebensmittel/Restaurant + Freizeit/Sport)

"Haushalt Q1 2025":
  4 Transaktionen aus Jan–Mär 2025 (Wohnen/Miete + Wohnen/Internet)
```

---

### Unit-Tests pro Migrations-Phase

Die Tests liegen in
`web/src/test/kotlin/com/moneylytics/api/application/service/`.
Jede Testklasse folgt dem Muster aus `TransactionQueryControllerTest`:
direkte Instanziierung, gemockte Repository-Abhängigkeiten.

#### Phase 2 — CashflowServiceTest

**Eingaben** (feste Transaktionen für Monat März 2025):
```kotlin
tx(date="2025-03-01", amount=-950.00,  effectiveAmount=-950.00),   // Miete
tx(date="2025-03-15", amount=-180.00,  effectiveAmount=-60.00),    // Arzt (genet.)
tx(date="2025-03-18", amount=+120.00,  effectiveAmount=0.00),      // Erstattung (gen.)
tx(date="2025-03-28", amount=+2850.00, effectiveAmount=+2850.00),  // Gehalt
```

**Erwartete Ausgabe** für `granularity=MONTHLY`:
```
Bucket "2025-03":
  incomeGross   = 2970.00   (2850 + 120)
  incomeNet     = 2850.00   (2850 + 0)
  expensesGross = 1130.00   (950 + 180)
  expensesNet   = 1010.00   (950 + 60)
  net           = 1840.00   (2850 - 1010)
```

**Tests:**
- `should bucket transactions by month with correct gross and net income`
- `should bucket transactions by month with correct gross and net expenses`
- `should fill empty months with zero values`
- `should aggregate by year when granularity is YEARLY`

#### Phase 3 — BurnRateServiceTest

**Eingaben** (Juni 2025, rollingWindow=3):
```kotlin
tx(date="2025-06-01", amount=-950.00, effectiveAmount=-950.00),
tx(date="2025-06-03", amount=-45.50,  effectiveAmount=-45.50),
tx(date="2025-06-05", amount=-120.00, effectiveAmount=-120.00),
tx(date="2025-06-28", amount=+2900.00, effectiveAmount=+2900.00),  // Einnahme
```

**Erwartete Ausgabe** (Auszug, rollingWindow=3):
```
Jun 01: expenses=950.00,  cumulative=950.00,  rollingAvg=950.00   (950/1)
Jun 02: expenses=0.00,    cumulative=950.00,  rollingAvg=475.00   (950+0)/2
Jun 03: expenses=45.50,   cumulative=995.50,  rollingAvg=331.83   (950+0+45.50)/3
Jun 04: expenses=0.00,    cumulative=995.50,  rollingAvg=15.17    (0+45.50+0)/3
Jun 05: expenses=120.00,  cumulative=1115.50, rollingAvg=55.17    (45.50+0+120)/3
```
```
totalExpenses = 1115.50
totalIncome   = 2900.00
avgPerDay     = 1115.50 / 30 = 37.18
```

**Tests:**
- `should aggregate expenses by date and fill gaps with zero`
- `should compute rolling average over configurable window`
- `should compute cumulative expenses and income`
- `should exclude positive transactions from expenses and negative from income`

#### Phase 4 — CategoryTotalsServiceTest

**Eingaben:**
```kotlin
tx(category="Lebensmittel", subcategory="Supermarkt",  amount=-80.00),
tx(category="Lebensmittel", subcategory="Restaurant",  amount=-45.00),
tx(category="Transport",    subcategory="ÖPNV",         amount=-86.00),
tx(category="Freizeit",     subcategory="Streaming",    amount=-18.00),
```

**Erwartete Ausgabe** (ohne category-Param):
```
[{name="Lebensmittel", value=125.00}, {name="Transport", value=86.00}, {name="Freizeit", value=18.00}]
(sortiert DESC)
```

**Erwartete Ausgabe** (mit `category=Lebensmittel`):
```
[{name="Supermarkt", value=80.00}, {name="Restaurant", value=45.00}]
```

**Tests:**
- `should return category totals sorted descending by value`
- `should return subcategory totals when category filter is applied`
- `should exclude income transactions`

#### Phase 5 — BudgetServiceTest (Erweiterung der bestehenden Tests)

**Eingaben:**
```kotlin
val link1 = BudgetTransactionLink(amount=null, transactionAmount=-380.00)
val link2 = BudgetTransactionLink(amount=null, transactionAmount=-490.00)
val link3 = BudgetTransactionLink(amount=BigDecimal("500"), transactionAmount=-800.00)
```

**Erwartete Ausgabe:**
```
Budget "Urlaub 2025":
  totalContributions = 870.00  (380 + 490)
  chartPoints = [{date="2025-04-10", cumulative=380.00},
                 {date="2025-05-22", cumulative=870.00}]

Budget "Neue Küche":
  totalContributions = 500.00  (partieller Link)
  chartPoints = [{date="2025-09-05", cumulative=500.00}]
```

**Tests:**
- `should calculate total contributions with full amounts`
- `should calculate total contributions with partial amounts`
- `should build sorted cumulative chart points`

---

## Bewusst NICHT im Scope

| Stelle | Begründung |
|---|---|
| Threshold-Normalisierung in TrendsPage | Ändert Trends-Response fundamental; separates Ticket |
| Farb-Zuweisung (`groupColorMap`) | Reine UI-Logik |
| Lookup-Maps (`accountMap`, `txBudgetMap`) | Render-Performance, keine Businesslogik |
| Chart-Format-Mapping (`lineData` in TrendsPage) | Chart-Library-spezifisch |

---

## Empfohlene Reihenfolge

| # | Phase | Aufwand | Wert |
|---|---|---|---|
| 1 | 1a — `type`-Param | S | Basis für alles weitere |
| 2 | 2 — `/cashflow` | M | Größter Datenvolumen-Gewinn |
| 3 | 3 — `/burnrate` | L | Komplexeste Logik |
| 4 | 4 — `/category-totals` | S | Klare Verbesserung |
| 5 | 5 — Budget-Felder | M | Entfernt Duplikation |
| 6 | 1b — `exclude`-Params | S | Braucht Budget-Service-Kenntnis |

---

## Tests & Verifikation (pro Phase)

- Unit-Tests für die Service-Methode (AAA, AssertJ, Mockito Kotlin, Backtick-Namen)
- Frontend: Seite manuell aufrufen, Zahlen visuell mit vorherigem Stand vergleichen
- `./gradlew :web:test` muss grün bleiben
- `./gradlew ktlintFormat` nach jeder neuen Kotlin-Datei
