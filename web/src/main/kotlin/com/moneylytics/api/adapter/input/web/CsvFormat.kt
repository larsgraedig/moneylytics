package com.moneylytics.api.adapter.input.web

/**
 * Registry of supported CSV layouts. Each entry declares the column names and
 * date pattern for one specific export format.
 *
 * Auto-detection in [CsvTransactionParser] picks the entry whose [CsvFormatConfig.requiredColumns]
 * are all present in the uploaded file's header row, so the caller never has to
 * specify a format explicitly.
 *
 * To add support for a new CSV layout, add an entry here — no other code needs
 * to change.
 */
enum class CsvFormat(
    val config: CsvFormatConfig,
) {
    /** German bank export (MLP Banking / MLP KomfortKonto style). */
    MLP_BANKING(
        CsvFormatConfig(
            name = "MLP Banking",
            category = "Kategorie",
            subcategory = "Unterkategorie",
            bookingDate = "Buchungstag",
            valueDate = "Valutadatum",
            amount = "Betrag",
            currency = "EUR",
            datePattern = "dd.MM.yyyy",
            accountIban = "IBAN Auftragskonto",
            accountName = "Bezeichnung Auftragskonto",
        ),
    ),

    /** Generic personal-finance export with English column names and ISO dates. */
    STANDARD(
        CsvFormatConfig(
            name = "Standard",
            category = "Main category",
            subcategory = "Second Category",
            bookingDate = "booking_date",
            valueDate = "valuta_date",
            amount = "amount",
            currency = "currency",
            datePattern = "yyyy-MM-dd",
            accountIban = "account_type",
        ),
    ),
}
