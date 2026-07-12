package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.adapter.output.persistence.CsvProfilePersistenceAdapter
import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.domain.Account
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User

class GenericCsvControllerTest {
    private val userId = 1L
    private val resolveUserUseCase: ResolveUserUseCase = mock { on { resolveUser(any()) } doReturn userId }
    private val detector: GenericCsvDetector = mock()
    private val parser: GenericCsvParser = mock()
    private val importTransactionsUseCase: ImportTransactionsUseCase = mock()
    private val checkDuplicatesUseCase: CheckDuplicatesUseCase = mock()
    private val getAccountsUseCase: GetAccountsUseCase = mock()
    private val enrichTransactionUseCase: EnrichTransactionUseCase = mock()
    private val csvProfileAdapter: CsvProfilePersistenceAdapter = mock()
    private val controller =
        GenericCsvController(
            detector,
            parser,
            importTransactionsUseCase,
            checkDuplicatesUseCase,
            getAccountsUseCase,
            enrichTransactionUseCase,
            resolveUserUseCase,
            csvProfileAdapter,
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
            whenever(getAccountsUseCase.getAccounts(userId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(3)

            val request =
                GenericCsvImportRequest(
                    toImport =
                        listOf(
                            row("DE01"),
                            row("DE02"),
                            row("DE03"),
                        ),
                )
            val response = controller.importRows(request, principal)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat((response.body as ImportSuccessResponse).importedCount).isEqualTo(3)
        }

    @Test
    fun `should filter rows to known IBANs when knownIbans is non-empty`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(userId)).thenReturn(
                listOf(Account(iban = "DE01", name = "Giro")),
            )
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(1)

            val request =
                GenericCsvImportRequest(
                    toImport =
                        listOf(
                            row("DE01"),
                            row("DE99"),
                        ),
                )
            controller.importRows(request, principal)

            val captor = argumentCaptor<com.moneylytics.api.application.port.input.ImportTransactionsCommand>()
            verify(importTransactionsUseCase).importTransactions(captor.capture())
            assertThat(captor.firstValue.transactions).hasSize(1)
            assertThat(captor.firstValue.transactions[0].accountIban).isEqualTo("DE01")
        }

    @Test
    fun `should use iban as key and value in accountNames map for importRows`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(userId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(1)

            controller.importRows(GenericCsvImportRequest(toImport = listOf(row("DE01"))), principal)

            val captor = argumentCaptor<com.moneylytics.api.application.port.input.ImportTransactionsCommand>()
            verify(importTransactionsUseCase).importTransactions(captor.capture())
            assertThat(captor.firstValue.accountNames).containsEntry("DE01", "DE01")
        }

    @Test
    fun `should call enrichByFingerprint for each toEnrich entry in importRows`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(userId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(0)

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
            controller.importRows(request, principal)

            verify(enrichTransactionUseCase).enrichByFingerprint("fp1", userId, "Test", null, null)
        }

    private fun row(iban: String) =
        GenericRowToImport(
            date = "2025-01-15",
            amount = -100.0,
            currency = "EUR",
            accountIban = iban,
            purpose = null,
            category = "Sonstiges",
            subcategory = "Sonstiges",
        )
}
