# Moneylytics Pricing Tiers

Grundprinzip: Free hakt, Starter bindet, Plus überzeugt Power-User, Pro monetarisiert Familien und Kleinstunternehmer.

---

## Free — €0

**Zweck:** Einstieg senken, App ausprobieren lassen, Hunger wecken.

| Feature | Einschränkung |
|---|---|
| Transaktionshistorie | Nur letzten 2 Monate sichtbar |
| Konten | Max. 1 Konto |
| Import | Kein CSV/CAMT-Import |
| Kategorien | Max. 10, flach (keine Hierarchie) |
| Charts | Nur Transaktionsliste + einfache Breakdown-Pie |
| Sankey | Nicht verfügbar |
| Budgets | Nicht verfügbar |
| Limits/Alerts | Nicht verfügbar |
| Organisationen | 1, nur Einzelnutzer |

**Upgrade-Trigger:** Nutzer möchte älteren Kontoauszug importieren oder sieht die Sankey-Preview und will sie nutzen.

---

## Starter — €3,99/Monat (€39,99/Jahr)

**Zweck:** Der "Aha-Moment"-Tier. Fühlt sich für normale Nutzer vollständig an. Ausreichend attraktiv zum Binden, aber klar begrenzt für ambitioniertere Nutzer.

| Feature | Details |
|---|---|
| Transaktionshistorie | Unbegrenzt |
| Konten | Unbegrenzt |
| Import | CSV + CAMT, inkl. Duplikaterkennung |
| Kategorien | Hierarchisch, unbegrenzt |
| **Sankey-Diagramm** | ✅ — der visuelle Showstopper, der Starter-Upgrades treibt |
| Breakdown + Trends | ✅ |
| Cashflow-Chart | ✅ |
| Virtuelle Transaktionen | ✅ |
| Transaktionen splitten/mergen | ✅ |
| Collections | ✅ |
| Organisationen | 1, nur Einzelnutzer |
| Budgets | Nicht verfügbar |
| Limits/Alerts | Nicht verfügbar |
| Wiederkehrer | Nicht verfügbar |
| KI-Kategorisierung | Nicht verfügbar |

**Upgrade-Trigger:** Nutzer möchte Budgets anlegen, Daueraufträge automatisch erkennen lassen, oder Ausgaben-Alerts einrichten.

---

## Plus — €7,99/Monat (€74,99/Jahr)

**Zweck:** Power-User-Tier. Automation, Planung und proaktive Kontrolle.

| Feature | Details |
|---|---|
| Alles aus Starter | ✅ |
| **KI-Kategorisierung** | ✅ — spart Zeit bei Imports |
| **Budgets** | Unbegrenzt, mit Transaktionszuweisung |
| **Spending Limits/Alerts** | Monatlich/Quartal/Jährlich, 3 Stufen |
| **Wiederkehrer-Erkennung** | Automatische Erkennung + Management |
| **Transaktionsverknüpfungen** | Interne Transfers/Verrechnungen |
| Burn Rate Chart | ✅ |
| Bulk-Kategorie-Updates | ✅ |
| Organisationen | 1, nur Einzelnutzer |

**Upgrade-Trigger:** Nutzer möchte weitere Organisationen anlegen (Privat + Firma) oder den Account mit dem/der Partner/in teilen.

---

## Pro — €14,99/Monat (€139,99/Jahr)

**Zweck:** Für Paare, Familien, oder Nutzer mit mehreren Finanzkreisen (privat + freiberuflich).

| Feature | Details |
|---|---|
| Alles aus Plus | ✅ |
| **Mehrere Organisationen** | Unbegrenzt (z.B. Privat + Freiberuflich) |
| **Team-Collaboration** | Mitglieder einladen, Rollen (Admin/Member) |
| **Organisations-Logo** | Custom Branding |
| Prioritäts-Support | ✅ |

---

## Preislogik & Positionierung

- **Free → Starter (€3,99):** Die Grenze liegt beim Import und der Sankey. Die Sankey ist der visuell eindrucksvollste Feature der App. €3,99 ist ein Impulskauf; €39,99/Jahr liegt psychologisch unter der dreistelligen Schwelle.

- **Starter → Plus (€7,99):** Der Schritt von "App nutzen" zu "App als Werkzeug nutzen". Budgets und Wiederkehrer-Erkennung lösen echte Schmerzpunkte (Überblick verlieren, Abos nicht im Blick haben).

- **Plus → Pro (€14,99):** Relativer Preissprung klein (~€7), aber der Use Case ist spezifisch. Für Paare, die gemeinsame Finanzen verwalten, ist geteilter Zugang ein starkes Argument.

**Jährliche Rabatte:** ~17% auf alle bezahlten Tiers. Motiviert Jahresabschlüsse und verbessert Cashflow.

---

## Technische Umsetzung (Roadmap)

1. `subscription_tier`-Feld in User-/Org-Modell (`FREE | STARTER | PLUS | PRO`) + Ablaufdatum — Flyway-Migration
2. Feature-Gates im Backend: Service-Layer-Checks vor kritischen Endpunkten (Import, Budget-Create, etc.)
3. Frontend-Gates: Gesperrte UI-Elemente mit Upgrade-Hinweis statt Fehlermeldung
4. Datenbeschränkung: Transaktions-Query in Free-Tier auf 2 Monate einschränken
5. Stripe oder Paddle für Self-Service-Subscription-Management
6. Jährliche Billing-Option
