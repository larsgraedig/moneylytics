package com.moneylytics.api.adapter.input.web

/**
 * Describes how a CSV file's columns map onto the Transaction domain fields,
 * and which date pattern its date columns use.
 *
 * Add a new entry to [CsvFormat] to support an additional CSV layout without
 * touching any parsing logic.
 */
data class CsvFormatConfig(
    val name: String,
    val bookingDate: String,
    val valueDate: String,
    val amount: String,
    val currency: String,
    /** Column whose value is used as the account identifier (IBAN or name). */
    val accountIban: String,
    val datePattern: String,
    /** Column whose value is used as the transaction category, or null if the format has no category column. */
    val category: String? = null,
    /** Column whose value is used as the transaction subcategory, or null if the format has no subcategory column. */
    val subcategory: String? = null,
    /** Column whose value is used as the human-readable account name, or null to reuse [accountIban]. */
    val accountName: String? = null,
    /** Column whose value is used as the transaction purpose (Verwendungszweck), or null if absent. */
    val purpose: String? = null,
) {
    val requiredColumns: Set<String> =
        setOf(
            bookingDate,
            valueDate,
            amount,
            currency,
            accountIban,
        )
}
