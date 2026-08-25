package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.adapter.output.persistence.CsvProfilePersistenceAdapter
import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsResult
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
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
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.core.userdetails.User
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class GenericCsvControllerTest {
    private val organizationId = 1L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val detector: GenericCsvDetector = mock()
    private val parser: GenericCsvParser = mock()
    private val importTransactionsUseCase: ImportTransactionsUseCase = mock()
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase = mock()
    private val getAccountsUseCase: GetAccountsUseCase = mock()
    private val enrichTransactionUseCase: EnrichTransactionUseCase = mock()
    private val csvProfileAdapter: CsvProfilePersistenceAdapter = mock()
    private val categoryClassifier: CategoryClassifier = mock()
    private val controller =
        GenericCsvController(
            detector,
            parser,
            importTransactionsUseCase,
            checkDuplicatesUseCase,
            getAccountsUseCase,
            enrichTransactionUseCase,
            resolveOrganizationUseCase,
            csvProfileAdapter,
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
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(3, 1L))

            val request =
                GenericCsvImportRequest(
                    toImport =
                        listOf(
                            row("DE01"),
                            row("DE02"),
                            row("DE03"),
                        ),
                )
            val response = controller.importRows(request, principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat((response.body as ImportSuccessResponse).importedCount).isEqualTo(3)
        }

    @Test
    fun `should filter rows to known IBANs when knownIbans is non-empty`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(
                listOf(Account(iban = "DE01", name = "Giro")),
            )
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(1, 1L))

            val request =
                GenericCsvImportRequest(
                    toImport =
                        listOf(
                            row("DE01"),
                            row("DE99"),
                        ),
                )
            controller.importRows(request, principal, exchange)

            val captor = argumentCaptor<com.moneylytics.api.application.port.input.ImportTransactionsCommand>()
            verify(importTransactionsUseCase).importTransactions(captor.capture())
            assertThat(captor.firstValue.transactions).hasSize(1)
            assertThat(captor.firstValue.transactions[0].accountIban).isEqualTo("DE01")
        }

    @Test
    fun `should use iban as key and value in accountNames map for importRows`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(1, 1L))

            controller.importRows(GenericCsvImportRequest(toImport = listOf(row("DE01"))), principal, exchange)

            val captor = argumentCaptor<com.moneylytics.api.application.port.input.ImportTransactionsCommand>()
            verify(importTransactionsUseCase).importTransactions(captor.capture())
            assertThat(captor.firstValue.accountNames).containsEntry("DE01", "DE01")
        }

    @Test
    fun `should call enrichByFingerprint for each toEnrich entry in importRows`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(ImportTransactionsResult(0, 1L))

            val request =
                GenericCsvImportRequest(
                    toImport = emptyList(),
                    toEnrich =
                        listOf(
                            TransactionEnrichRequest(
                                fingerprint = "fp1",
                                purpose = "Test",
                                counterpartyName = null,
                                counterpartyIban = null,
                            ),
                        ),
                )
            controller.importRows(request, principal, exchange)

            verify(enrichTransactionUseCase).enrichByFingerprint("fp1", organizationId, "Test", null, null)
        }

    @Test
    fun `should combine rows from multiple files and assign sourceFilename in preview`() =
        runTest {
            val mapping = aMapping()
            val file1Rows =
                listOf(
                    previewRow(rowIndex = 0, fingerprint = "fp1", accountIban = "DE01"),
                    previewRow(rowIndex = 1, fingerprint = "fp2", accountIban = "DE01"),
                )
            val file2Rows =
                listOf(
                    previewRow(rowIndex = 0, fingerprint = "fp3", accountIban = "DE01"),
                )
            val filePart1 = mockFilePart("csv-content-1", "january.csv")
            val filePart2 = mockFilePart("csv-content-2", "february.csv")
            whenever(parser.preview("csv-content-1", mapping)).thenReturn(file1Rows)
            whenever(parser.preview("csv-content-2", mapping)).thenReturn(file2Rows)
            whenever(checkDuplicatesUseCase.findExistingFingerprints(any(), any())).thenReturn(emptySet())
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(categoryClassifier.suggestAll(any(), any())).thenReturn(listOf(null, null, null))
            val multipartData = LinkedMultiValueMap<String, org.springframework.http.codec.multipart.Part>()
            multipartData.add("files", filePart1)
            multipartData.add("files", filePart2)
            whenever(exchange.multipartData).thenReturn(Mono.just(multipartData))

            val result = controller.preview(mapping, principal, exchange)

            assertThat(result).hasSize(3)
            assertThat(result[0].rowIndex).isEqualTo(0)
            assertThat(result[0].sourceFilename).isEqualTo("january.csv")
            assertThat(result[1].rowIndex).isEqualTo(1)
            assertThat(result[1].sourceFilename).isEqualTo("january.csv")
            assertThat(result[2].rowIndex).isEqualTo(2)
            assertThat(result[2].sourceFilename).isEqualTo("february.csv")
        }

    @Test
    fun `should return empty list when no files provided in preview`() =
        runTest {
            val multipartData = LinkedMultiValueMap<String, org.springframework.http.codec.multipart.Part>()
            whenever(exchange.multipartData).thenReturn(Mono.just(multipartData))

            val result = controller.preview(aMapping(), principal, exchange)

            assertThat(result).isEmpty()
        }

    private fun aMapping() =
        GenericCsvMapping(
            delimiter = ";",
            dateColumn = "Datum",
            dateFormat = "dd.MM.yyyy",
            amountColumn = "Betrag",
            amountFormat = AmountFormat.GERMAN,
            purposeColumn = null,
            categoryColumn = null,
            groupColumn = null,
            accountIbanColumn = "IBAN",
            currencyColumn = null,
            fixedAccountIban = null,
            fixedCurrency = "EUR",
        )

    private fun previewRow(
        rowIndex: Int,
        fingerprint: String,
        accountIban: String,
    ) = GenericCsvPreviewRow(
        rowIndex = rowIndex,
        date = "2025-01-15",
        amount = -100.0,
        currency = "EUR",
        accountIban = accountIban,
        purpose = null,
        fingerprint = fingerprint,
        status = RowStatus.NEW,
        unknownAccount = false,
        mappedCategory = null,
        mappedGroup = null,
    )

    private fun mockFilePart(
        content: String,
        filename: String,
    ): FilePart {
        val filePart: FilePart = mock()
        whenever(filePart.filename()).thenReturn(filename)
        val bytes = content.toByteArray(Charsets.UTF_8)
        val buffer = DefaultDataBufferFactory.sharedInstance.wrap(bytes)
        whenever(filePart.content()).thenReturn(Flux.just(buffer))
        return filePart
    }

    private fun row(iban: String) =
        GenericRowToImport(
            date = "2025-01-15",
            amount = -100.0,
            currency = "EUR",
            accountIban = iban,
            purpose = null,
            category = "Sonstiges",
            group = "Sonstiges",
        )
}
