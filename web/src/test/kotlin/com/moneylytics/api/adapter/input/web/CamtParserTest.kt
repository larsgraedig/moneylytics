package com.moneylytics.api.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class CamtParserTest {
    private val parser = CamtParser()

    @Test
    fun `should return FormatError when XML is invalid`() {
        val result = parser.parse("not xml at all".toByteArray())

        assertThat(result).isInstanceOf(CamtParseResult.FormatError::class.java)
    }

    @Test
    fun `should return FormatError when XML has no Rpt elements`() {
        val xml = "<Document><BkToCstmrAcctRpt></BkToCstmrAcctRpt></Document>"

        val result = parser.parse(xml.toByteArray())

        assertThat(result).isInstanceOf(CamtParseResult.FormatError::class.java)
        assertThat((result as CamtParseResult.FormatError).message).contains("Rpt")
    }

    @Test
    fun `should negate amount for DBIT entries`() {
        val result = parser.parse(camt(entry(amount = "950.00", cdtDbt = "DBIT")))

        val success = result as CamtParseResult.Success
        assertThat(success.rows[0].amount).isEqualByComparingTo(BigDecimal("-950.00"))
    }

    @Test
    fun `should keep amount positive for CRDT entries`() {
        val result = parser.parse(camt(entry(amount = "2500.00", cdtDbt = "CRDT")))

        val success = result as CamtParseResult.Success
        assertThat(success.rows[0].amount).isEqualByComparingTo(BigDecimal("2500.00"))
    }

    @Test
    fun `should parse booking and value dates`() {
        val result = parser.parse(camt(entry(bookDate = "2025-06-15", valueDate = "2025-06-17")))

        val success = result as CamtParseResult.Success
        assertThat(success.rows[0].bookingDate).isEqualTo(LocalDate.of(2025, 6, 15))
        assertThat(success.rows[0].valueDate).isEqualTo(LocalDate.of(2025, 6, 17))
    }

    @Test
    fun `should fall back to booking date when value date is missing`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TEST</IBAN></Id></Acct>
            <Ntry>
              <Amt Ccy="EUR">50.00</Amt><CdtDbtInd>CRDT</CdtDbtInd>
              <BookgDt><Dt>2025-03-10</Dt></BookgDt>
            </Ntry>
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val result = parser.parse(xml.toByteArray())

        val success = result as CamtParseResult.Success
        assertThat(success.rows[0].bookingDate).isEqualTo(LocalDate.of(2025, 3, 10))
        assertThat(success.rows[0].valueDateRaw).isEqualTo("2025-03-10")
    }

    @Test
    fun `should extract counterparty name and IBAN for DBIT from Cdtr`() {
        val result =
            parser.parse(
                camt(
                    entry(
                        cdtDbt = "DBIT",
                        cdtrName = "Landlord GmbH",
                        cdtrIban = "DE99LANDLORD",
                    ),
                ),
            )

        val row = (result as CamtParseResult.Success).rows[0]
        assertThat(row.counterparty).isEqualTo("Landlord GmbH")
        assertThat(row.counterpartyIban).isEqualTo("DE99LANDLORD")
    }

    @Test
    fun `should extract counterparty name and IBAN for CRDT from Dbtr`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TEST</IBAN></Id></Acct>
            <Ntry>
              <Amt Ccy="EUR">1000.00</Amt><CdtDbtInd>CRDT</CdtDbtInd>
              <BookgDt><Dt>2025-01-28</Dt></BookgDt>
              <NtryDtls><TxDtls><RltdPties>
                <Dbtr><Nm>Arbeitgeber AG</Nm></Dbtr>
                <DbtrAcct><Id><IBAN>DE88EMPLOYER</IBAN></Id></DbtrAcct>
              </RltdPties></TxDtls></NtryDtls>
            </Ntry>
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val row = (parser.parse(xml.toByteArray()) as CamtParseResult.Success).rows[0]

        assertThat(row.counterparty).isEqualTo("Arbeitgeber AG")
        assertThat(row.counterpartyIban).isEqualTo("DE88EMPLOYER")
    }

    @Test
    fun `should extract purpose from RmtInf Ustrd`() {
        val result = parser.parse(camt(entry(purpose = "Miete Januar 2025")))

        assertThat((result as CamtParseResult.Success).rows[0].purpose).isEqualTo("Miete Januar 2025")
    }

    @Test
    fun `should fall back to AddtlNtryInf for purpose when no RmtInf`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TEST</IBAN></Id></Acct>
            <Ntry>
              <Amt Ccy="EUR">10.00</Amt><CdtDbtInd>CRDT</CdtDbtInd>
              <BookgDt><Dt>2025-01-01</Dt></BookgDt>
              <AddtlNtryInf>Fallback purpose</AddtlNtryInf>
            </Ntry>
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val row = (parser.parse(xml.toByteArray()) as CamtParseResult.Success).rows[0]

        assertThat(row.purpose).isEqualTo("Fallback purpose")
    }

    @Test
    fun `should add error and null amount for invalid amount value`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TEST</IBAN></Id></Acct>
            <Ntry>
              <Amt Ccy="EUR">not-a-number</Amt><CdtDbtInd>DBIT</CdtDbtInd>
              <BookgDt><Dt>2025-01-01</Dt></BookgDt>
            </Ntry>
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val success = parser.parse(xml.toByteArray()) as CamtParseResult.Success
        val row = success.rows[0]

        assertThat(row.amount).isNull()
        assertThat(row.errors).anyMatch { it.column == "Amt" }
    }

    @Test
    fun `should add error and null date for invalid date format`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TEST</IBAN></Id></Acct>
            <Ntry>
              <Amt Ccy="EUR">50.00</Amt><CdtDbtInd>DBIT</CdtDbtInd>
              <BookgDt><Dt>15.01.2025</Dt></BookgDt>
            </Ntry>
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val success = parser.parse(xml.toByteArray()) as CamtParseResult.Success
        val row = success.rows[0]

        assertThat(row.bookingDate).isNull()
        assertThat(row.errors).anyMatch { it.column == "BookgDt" }
    }

    @Test
    fun `should add error when account IBAN is blank`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct></Acct>
            <Ntry>
              <Amt Ccy="EUR">50.00</Amt><CdtDbtInd>DBIT</CdtDbtInd>
              <BookgDt><Dt>2025-01-01</Dt></BookgDt>
            </Ntry>
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val success = parser.parse(xml.toByteArray()) as CamtParseResult.Success
        val row = success.rows[0]

        assertThat(row.errors).anyMatch { it.column == "IBAN" }
    }

    @Test
    fun `should parse account IBAN and currency from entry`() {
        val result = parser.parse(camt(entry(amount = "100.00", cdtDbt = "DBIT"), iban = "DE12345678"))

        val row = (result as CamtParseResult.Success).rows[0]
        assertThat(row.accountIban).isEqualTo("DE12345678")
        assertThat(row.currency).isEqualTo("EUR")
    }

    @Test
    fun `should assign incremental row numbers across multiple entries`() {
        val result = parser.parse(camt(entry(), entry()))

        val success = result as CamtParseResult.Success
        assertThat(success.rows).hasSize(2)
        assertThat(success.rows[0].rowNumber).isEqualTo(1)
        assertThat(success.rows[1].rowNumber).isEqualTo(2)
    }

    @Test
    fun `should extract CLBD closing balance from Bal element`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TEST</IBAN></Id></Acct>
            <Bal>
              <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
              <Amt Ccy="EUR">3500.00</Amt>
              <CdtDbtInd>CRDT</CdtDbtInd>
              <Dt><Dt>2025-01-31</Dt></Dt>
            </Bal>
            ${entry()}
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val success = parser.parse(xml.toByteArray()) as CamtParseResult.Success

        assertThat(success.accountBalances).containsKey("DE00TEST")
        val balance = success.accountBalances["DE00TEST"]!!
        assertThat(balance.amount).isEqualByComparingTo(BigDecimal("3500.00"))
        assertThat(balance.date).isEqualTo(LocalDate.of(2025, 1, 31))
    }

    @Test
    fun `should negate CLBD balance when CdtDbtInd is DBIT`() {
        val xml =
            """<Document><BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TEST</IBAN></Id></Acct>
            <Bal>
              <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
              <Amt Ccy="EUR">200.00</Amt>
              <CdtDbtInd>DBIT</CdtDbtInd>
              <Dt><Dt>2025-02-28</Dt></Dt>
            </Bal>
            ${entry()}
            </Rpt></BkToCstmrAcctRpt></Document>"""

        val success = parser.parse(xml.toByteArray()) as CamtParseResult.Success

        assertThat(success.accountBalances["DE00TEST"]!!.amount).isEqualByComparingTo(BigDecimal("-200.00"))
    }

    @Test
    fun `should return empty accountBalances when no Bal element is present`() {
        val result = parser.parse(camt(entry()))

        assertThat((result as CamtParseResult.Success).accountBalances).isEmpty()
    }

    private fun camt(
        vararg entries: String,
        iban: String = "DE00TEST123",
    ) = """<Document><BkToCstmrAcctRpt><Rpt>
        <Acct><Id><IBAN>$iban</IBAN></Id><Nm>Test</Nm></Acct>
        ${entries.joinToString("\n")}
        </Rpt></BkToCstmrAcctRpt></Document>""".toByteArray()

    private fun entry(
        amount: String = "100.00",
        cdtDbt: String = "DBIT",
        bookDate: String = "2025-01-15",
        valueDate: String = "2025-01-15",
        cdtrName: String = "Test GmbH",
        cdtrIban: String? = "DE99CDTR",
        purpose: String? = "Test purpose",
    ) = """<Ntry>
        <Amt Ccy="EUR">$amount</Amt>
        <CdtDbtInd>$cdtDbt</CdtDbtInd>
        <BookgDt><Dt>$bookDate</Dt></BookgDt>
        <ValDt><Dt>$valueDate</Dt></ValDt>
        <NtryDtls><TxDtls>
          <RltdPties>
            <Cdtr><Nm>$cdtrName</Nm></Cdtr>
            ${if (cdtrIban != null) "<CdtrAcct><Id><IBAN>$cdtrIban</IBAN></Id></CdtrAcct>" else ""}
          </RltdPties>
          ${if (purpose != null) "<RmtInf><Ustrd>$purpose</Ustrd></RmtInf>" else ""}
        </TxDtls></NtryDtls>
        </Ntry>"""
}
