package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.TransactionImportRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Account
import com.moneylytics.api.domain.AccountBalance
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.ImportStatus
import com.moneylytics.api.domain.Transaction
import com.moneylytics.api.domain.TransactionImport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class TransactionImportServiceTest {
    private val transactionRepository: TransactionRepository = mock()
    private val accountRepository: AccountRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val categoryClassifier: CategoryClassifier = mock()
    private val transactionImportRepository: TransactionImportRepository = mock()
    private val service =
        TransactionImportService(
            transactionRepository,
            accountRepository,
            categoryRepository,
            categoryClassifier,
            transactionImportRepository,
        )

    private val organizationId = 1L
    private val date = LocalDate.of(2025, 1, 15)
    private val savedImport =
        TransactionImport(
            id = 42L,
            organizationId = organizationId,
            filename = "test.csv",
            checksum = "abc",
            fileType = ImportFileType.CSV,
            transactionCount = 0,
            status = ImportStatus.ACTIVE,
        )

    private fun baseCommand(transactions: List<Transaction>) =
        ImportTransactionsCommand(
            transactions = transactions,
            accountNames = mapOf("DE01" to "Giro"),
            organizationId = organizationId,
            filename = "test.csv",
            checksum = "abc",
            fileType = ImportFileType.CSV,
        )

    @Test
    fun `should create new account when IBAN is not yet known`() {
        val tx = tx("DE01")
        val command = baseCommand(listOf(tx))
        whenever(accountRepository.findByIban("DE01", organizationId)).thenReturn(null)
        whenever(transactionRepository.saveAll(listOf(tx), organizationId)).thenReturn(1 to listOf(1L))
        whenever(transactionImportRepository.save(any())).thenReturn(savedImport)

        val result = service.importTransactions(command)

        verify(accountRepository).save(Account(iban = "DE01", name = "Giro"), organizationId)
        assertThat(result.importedCount).isEqualTo(1)
    }

    @Test
    fun `should not create account when IBAN already exists`() {
        val tx = tx("DE01")
        val command = baseCommand(listOf(tx))
        whenever(accountRepository.findByIban("DE01", organizationId)).thenReturn(Account(iban = "DE01", name = "Giro"))
        whenever(transactionRepository.saveAll(listOf(tx), organizationId)).thenReturn(1 to listOf(1L))
        whenever(transactionImportRepository.save(any())).thenReturn(savedImport)

        service.importTransactions(command)

        verify(accountRepository, never()).save(any(), any())
    }

    @Test
    fun `should return transaction count from repository`() {
        val transactions = listOf(tx("DE01"), tx("DE01"))
        val command = baseCommand(transactions)
        whenever(accountRepository.findByIban("DE01", organizationId)).thenReturn(Account(iban = "DE01", name = "Giro"))
        whenever(transactionRepository.saveAll(transactions, organizationId)).thenReturn(2 to listOf(1L, 2L))
        whenever(transactionImportRepository.save(any())).thenReturn(savedImport)

        val result = service.importTransactions(command)

        assertThat(result.importedCount).isEqualTo(2)
    }

    @Test
    fun `should update account balance when accountBalances is provided`() {
        val tx = tx("DE01")
        val balance = AccountBalance(amount = BigDecimal("1500.00"), date = date)
        val command =
            baseCommand(listOf(tx)).copy(accountBalances = mapOf("DE01" to balance))
        whenever(accountRepository.findByIban("DE01", organizationId)).thenReturn(Account(iban = "DE01", name = "Giro"))
        whenever(transactionRepository.saveAll(listOf(tx), organizationId)).thenReturn(1 to listOf(1L))
        whenever(transactionImportRepository.save(any())).thenReturn(savedImport)

        service.importTransactions(command)

        verify(accountRepository).updateBalance("DE01", organizationId, balance.amount, balance.date)
    }

    @Test
    fun `should not call updateBalance when no accountBalances provided`() {
        val tx = tx("DE01")
        val command = baseCommand(listOf(tx))
        whenever(accountRepository.findByIban("DE01", organizationId)).thenReturn(Account(iban = "DE01", name = "Giro"))
        whenever(transactionRepository.saveAll(listOf(tx), organizationId)).thenReturn(1 to listOf(1L))
        whenever(transactionImportRepository.save(any())).thenReturn(savedImport)

        service.importTransactions(command)

        verify(accountRepository, never()).updateBalance(any(), any(), any(), any())
    }

    private fun tx(iban: String) =
        Transaction(
            category = null,
            subcategory = null,
            bookingDate = date,
            valueDate = date,
            accountingDate = date,
            amount = BigDecimal("-50.00"),
            currency = "EUR",
            accountIban = iban,
        )
}
