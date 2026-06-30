package com.moneylytics.api.adapter.input.web

import org.springframework.stereotype.Component
import org.w3c.dom.Element
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

sealed interface CamtParseResult {
    data class FormatError(
        val message: String,
    ) : CamtParseResult

    data class Success(
        val rows: List<ParsedRawRow>,
    ) : CamtParseResult
}

@Component
class CamtParser {
    fun parse(bytes: ByteArray): CamtParseResult {
        val doc =
            try {
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                factory.newDocumentBuilder().parse(bytes.inputStream())
            } catch (e: Exception) {
                return CamtParseResult.FormatError("Invalid XML: ${e.message}")
            }

        val reports = doc.getElementsByTagNameNS("*", "Rpt")
        if (reports.length == 0) {
            return CamtParseResult.FormatError("No Rpt elements found — not a CAMT.052 file")
        }

        val rows = mutableListOf<ParsedRawRow>()
        var rowCounter = 1

        for (rptIdx in 0 until reports.length) {
            val rpt = reports.item(rptIdx) as Element

            val acctEl = rpt.firstChildEl("Acct")
            val acctIban =
                acctEl
                    ?.firstChildEl("Id")
                    ?.firstChildEl("IBAN")
                    ?.textContent
                    ?.trim() ?: ""
            val acctName = acctEl?.firstChildEl("Nm")?.textContent?.trim() ?: ""

            val entries = rpt.childNodes.elements().filter { it.localName == "Ntry" }

            for (entry in entries) {
                val errors = mutableListOf<ParsedRawError>()

                val amtEl = entry.firstChildEl("Amt")
                val amtRaw = amtEl?.textContent?.trim() ?: ""
                val currency = amtEl?.getAttribute("Ccy") ?: ""
                val cdtDbt = entry.firstChildEl("CdtDbtInd")?.textContent?.trim() ?: ""

                val amount = parseAmount(amtRaw, cdtDbt, errors)

                val bookingDateRaw =
                    entry
                        .firstChildEl("BookgDt")
                        ?.firstChildEl("Dt")
                        ?.textContent
                        ?.trim() ?: ""
                val valueDateRaw =
                    entry
                        .firstChildEl("ValDt")
                        ?.firstChildEl("Dt")
                        ?.textContent
                        ?.trim()
                        ?: bookingDateRaw

                val bookingDate = parseDate(bookingDateRaw, "BookgDt", errors)
                val valueDate = parseDate(valueDateRaw, "ValDt", errors)

                val txDtls = entry.firstChildEl("NtryDtls")?.firstChildEl("TxDtls")
                val rltdPties = txDtls?.firstChildEl("RltdPties")
                val (counterparty, counterpartyIban) = resolveCounterparty(rltdPties, cdtDbt)
                val purpose =
                    txDtls
                        ?.firstChildEl("RmtInf")
                        ?.firstChildEl("Ustrd")
                        ?.textContent
                        ?.trim()
                        ?: entry.firstChildEl("AddtlNtryInf")?.textContent?.trim()
                        ?: ""

                if (acctIban.isBlank()) {
                    errors.add(ParsedRawError("IBAN", acctIban, "Account IBAN not found in file"))
                }

                rows.add(
                    ParsedRawRow(
                        rowNumber = rowCounter++,
                        bookingDate = bookingDate,
                        valueDate = valueDate,
                        bookingDateRaw = bookingDateRaw,
                        valueDateRaw = valueDateRaw,
                        counterparty = counterparty,
                        counterpartyIban = counterpartyIban,
                        purpose = purpose,
                        amount = amount,
                        amountRaw = amtRaw,
                        currency = currency,
                        accountIban = acctIban,
                        accountName = acctName,
                        errors = errors,
                    ),
                )
            }
        }

        return CamtParseResult.Success(rows)
    }

    private fun resolveCounterparty(
        rltdPties: Element?,
        cdtDbt: String,
    ): Pair<String, String?> {
        if (rltdPties == null) return "" to null
        return when (cdtDbt) {
            "DBIT" -> {
                val name = rltdPties.firstChildEl("Cdtr")?.partyName()
                    ?: rltdPties.firstChildEl("Dbtr")?.partyName() ?: ""
                val iban = rltdPties.firstChildEl("CdtrAcct")?.ibanText()
                    ?: rltdPties.firstChildEl("DbtrAcct")?.ibanText()
                name to iban
            }
            else -> {
                val name = rltdPties.firstChildEl("Dbtr")?.partyName()
                    ?: rltdPties.firstChildEl("Cdtr")?.partyName() ?: ""
                val iban = rltdPties.firstChildEl("DbtrAcct")?.ibanText()
                    ?: rltdPties.firstChildEl("CdtrAcct")?.ibanText()
                name to iban
            }
        }
    }

    private fun Element.ibanText(): String? =
        firstChildEl("Id")?.firstChildEl("IBAN")?.textContent?.trim()?.takeIf { it.isNotBlank() }

    private fun Element.partyName(): String? {
        val direct = firstChildEl("Nm")?.textContent?.trim()
        val viaPty = firstChildEl("Pty")?.firstChildEl("Nm")?.textContent?.trim()
        return direct?.takeIf { it.isNotBlank() } ?: viaPty?.takeIf { it.isNotBlank() }
    }

    private fun parseDate(
        value: String,
        column: String,
        errors: MutableList<ParsedRawError>,
    ): LocalDate? {
        if (value.isBlank()) {
            errors.add(ParsedRawError(column, value, "Date must not be blank"))
            return null
        }
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            errors.add(ParsedRawError(column, value, "Invalid date format, expected yyyy-MM-dd"))
            null
        }
    }

    private fun parseAmount(
        value: String,
        cdtDbt: String,
        errors: MutableList<ParsedRawError>,
    ): BigDecimal? {
        if (value.isBlank()) {
            errors.add(ParsedRawError("Amt", value, "Amount must not be blank"))
            return null
        }
        return try {
            val abs = BigDecimal(value)
            if (cdtDbt == "DBIT") abs.negate() else abs
        } catch (_: NumberFormatException) {
            errors.add(ParsedRawError("Amt", value, "Invalid amount: $value"))
            null
        }
    }
}

private fun Element.firstChildEl(localName: String): Element? {
    val children = childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node is Element && node.localName == localName) return node
    }
    return null
}

private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }
