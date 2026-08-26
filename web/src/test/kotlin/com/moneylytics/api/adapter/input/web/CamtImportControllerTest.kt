package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.adapter.output.persistence.ImportPreviewSessionPersistenceAdapter
import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.FindIgnoredFingerprintsUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsResult
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.UpdateIgnoredTransactionsUseCase
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.domain.Account
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.util.UUID

class CamtImportControllerTest {
    private val organizationId = 1L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val camtParser: CamtParser = mock()
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase = mock()
    private val findIgnoredFingerprintsUseCase: FindIgnoredFingerprintsUseCase = mock()
    private val getAccountsUseCase: GetAccountsUseCase = mock()
    private val updateIgnoredTransactionsUseCase: UpdateIgnoredTransactionsUseCase = mock()
    private val importTransactionsUseCase: ImportTransactionsUseCase = mock()
    private val enrichTransactionUseCase: EnrichTransactionUseCase = mock()
    private val importPreviewSessionAdapter: ImportPreviewSessionPersistenceAdapter = mock()
    private val categoryClassifier: CategoryClassifier = mock()
    private val controller =
        CamtImportController(
            camtParser,
            checkDuplicatesUseCase,
            findIgnoredFingerprintsUseCase,
            getAccountsUseCase,
            updateIgnoredTransactionsUseCase,
            importTransactionsUseCase,
            enrichTransactionUseCase,
            resolveOrganizationUseCase,
            importPreviewSessionAdapter,
            categoryClassifier,
        )
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()

    @Test
    fun `should import all rows when knownIbans is empty`() =
        runTest {
            val token = UUID.randomUUID()
            whenever(importPreviewSessionAdapter.load(token, RawPreviewRow::class.java)).thenReturn(
                listOf(sessionRow("DE01", rowNumber = 1, fingerprint = "fp1"), sessionRow("DE02", rowNumber = 2, fingerprint = "fp2")),
            )
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(2, 1L))

            val response = controller.importCamt(importRequest(token), principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat((response.body as ImportSuccessResponse).importedCount).isEqualTo(2)
        }

    @Test
    fun `should filter out rows with unknown IBANs when knownIbans is non-empty`() =
        runTest {
            val token = UUID.randomUUID()
            whenever(importPreviewSessionAdapter.load(token, RawPreviewRow::class.java)).thenReturn(
                listOf(sessionRow("DE01", rowNumber = 1, fingerprint = "fp1"), sessionRow("DE99", rowNumber = 2, fingerprint = "fp2")),
            )
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(listOf(Account(iban = "DE01", name = "Giro")))
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(1, 1L))

            controller.importCamt(importRequest(token), principal, exchange)

            val captor = argumentCaptor<com.moneylytics.api.application.port.input.ImportTransactionsCommand>()
            verify(importTransactionsUseCase).importTransactions(captor.capture())
            assertThat(captor.firstValue.transactions).hasSize(1)
            assertThat(captor.firstValue.transactions[0].accountIban).isEqualTo("DE01")
        }

    @Test
    fun `should mark toImport fingerprints as toUnignore and toIgnore separately`() =
        runTest {
            val token = UUID.randomUUID()
            whenever(importPreviewSessionAdapter.load(token, RawPreviewRow::class.java)).thenReturn(
                listOf(sessionRow("DE01", rowNumber = 1, fingerprint = "fp-import")),
            )
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(1, 1L))

            controller.importCamt(importRequest(token, toIgnore = listOf("fp-ignore")), principal, exchange)

            verify(updateIgnoredTransactionsUseCase).update(
                toIgnore = listOf("fp-ignore"),
                toUnignore = listOf("fp-import"),
                organizationId = organizationId,
            )
        }

    @Test
    fun `should call enrichByFingerprint for each toEnrich entry`() =
        runTest {
            val token = UUID.randomUUID()
            whenever(importPreviewSessionAdapter.load(token, RawPreviewRow::class.java)).thenReturn(emptyList())
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(0, 1L))

            controller.importCamt(
                importRequest(
                    token,
                    toEnrich =
                        listOf(
                            TransactionEnrichRequest(
                                fingerprint = "fp1",
                                purpose = "Gehalt",
                                counterpartyName = "AG",
                                counterpartyIban = null,
                            ),
                        ),
                ),
                principal,
                exchange,
            )

            verify(enrichTransactionUseCase).enrichByFingerprint("fp1", organizationId, "Gehalt", "AG", null)
        }

    @Test
    fun `should return 404 when preview session is not found`() =
        runTest {
            val token = UUID.randomUUID()
            whenever(importPreviewSessionAdapter.load(token, RawPreviewRow::class.java)).thenReturn(null)

            val response = controller.importCamt(importRequest(token), principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

    @Test
    fun `should exclude rows by row number`() =
        runTest {
            val token = UUID.randomUUID()
            whenever(importPreviewSessionAdapter.load(token, RawPreviewRow::class.java)).thenReturn(
                listOf(sessionRow("DE01", rowNumber = 1, fingerprint = "fp1"), sessionRow("DE02", rowNumber = 2, fingerprint = "fp2")),
            )
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(1, 1L))

            controller.importCamt(importRequest(token, excludedRowIndices = listOf(2)), principal, exchange)

            val captor = argumentCaptor<com.moneylytics.api.application.port.input.ImportTransactionsCommand>()
            verify(importTransactionsUseCase).importTransactions(captor.capture())
            assertThat(captor.firstValue.transactions).hasSize(1)
            assertThat(captor.firstValue.transactions[0].accountIban).isEqualTo("DE01")
        }

    private fun importRequest(
        previewToken: UUID,
        excludedRowIndices: List<Int> = emptyList(),
        toIgnore: List<String> = emptyList(),
        toEnrich: List<TransactionEnrichRequest> = emptyList(),
    ) = CamtImportByTokenRequest(
        previewToken = previewToken,
        excludedRowIndices = excludedRowIndices,
        toIgnore = toIgnore,
        toEnrich = toEnrich,
        accountNames = mapOf("DE01" to "DE01", "DE02" to "DE02", "DE99" to "DE99"),
    )

    private fun sessionRow(
        iban: String,
        rowNumber: Int,
        fingerprint: String,
    ) = RawPreviewRow(
        rowNumber = rowNumber,
        status = RowStatus.NEW,
        bookingDate = "2025-01-15",
        valueDate = "2025-01-15",
        counterparty = "",
        purpose = "",
        amount = BigDecimal("-100.00"),
        amountRaw = "-100.00",
        currency = "EUR",
        accountIban = iban,
        accountName = iban,
        fingerprint = fingerprint,
        errors = emptyList(),
    )
}
