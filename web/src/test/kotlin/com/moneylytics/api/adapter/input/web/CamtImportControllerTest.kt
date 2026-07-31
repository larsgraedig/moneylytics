package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CheckDuplicatesUseCase
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.FindIgnoredFingerprintsUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.UpdateIgnoredTransactionsUseCase
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
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(2)

            val request =
                importRequest(
                    toImport =
                        listOf(
                            camtRow("DE01", "fp1"),
                            camtRow("DE02", "fp2"),
                        ),
                )
            val response = controller.importCamt(request, principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat((response.body as ImportSuccessResponse).importedCount).isEqualTo(2)
        }

    @Test
    fun `should filter out rows with unknown IBANs when knownIbans is non-empty`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(listOf(Account(iban = "DE01", name = "Giro")))
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(1)

            val request =
                importRequest(
                    toImport =
                        listOf(
                            camtRow("DE01", "fp1"),
                            camtRow("DE99", "fp2"),
                        ),
                )
            controller.importCamt(request, principal, exchange)

            val captor = argumentCaptor<com.moneylytics.api.application.port.input.ImportTransactionsCommand>()
            verify(importTransactionsUseCase).importTransactions(captor.capture())
            assertThat(captor.firstValue.transactions).hasSize(1)
            assertThat(captor.firstValue.transactions[0].accountIban).isEqualTo("DE01")
        }

    @Test
    fun `should mark toImport fingerprints as toUnignore and toIgnore separately`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(1)

            val request =
                importRequest(
                    toImport = listOf(camtRow("DE01", "fp-import")),
                    toIgnore = listOf("fp-ignore"),
                )
            controller.importCamt(request, principal, exchange)

            verify(updateIgnoredTransactionsUseCase).update(
                toIgnore = listOf("fp-ignore"),
                toUnignore = listOf("fp-import"),
                organizationId = organizationId,
            )
        }

    @Test
    fun `should call enrichByFingerprint for each toEnrich entry`() =
        runTest {
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(emptyList())
            whenever(importTransactionsUseCase.importTransactions(any())).thenReturn(0)

            val request =
                importRequest(
                    toImport = emptyList(),
                    toEnrich =
                        listOf(
                            TransactionEnrichRequest(
                                fingerprint = "fp1",
                                purpose = "Gehalt",
                                counterpartyName = "AG",
                                counterpartyIban = null,
                            ),
                        ),
                )
            controller.importCamt(request, principal, exchange)

            verify(enrichTransactionUseCase).enrichByFingerprint("fp1", organizationId, "Gehalt", "AG", null)
        }

    private fun importRequest(
        toImport: List<CamtTransactionImport>,
        toIgnore: List<String> = emptyList(),
        toEnrich: List<TransactionEnrichRequest> = emptyList(),
    ) = CamtImportRequest(
        accountNames = toImport.associate { it.accountIban to it.accountIban },
        toImport = toImport,
        toIgnore = toIgnore,
        toEnrich = toEnrich,
    )

    private fun camtRow(
        iban: String,
        fingerprint: String,
        category: String = "Sonstiges",
        group: String = "Sonstiges",
    ) = CamtTransactionImport(
        fingerprint = fingerprint,
        bookingDate = "2025-01-15",
        valueDate = "2025-01-15",
        amount = BigDecimal("-100.00"),
        currency = "EUR",
        category = category,
        group = group,
        accountIban = iban,
        purpose = null,
    )
}
