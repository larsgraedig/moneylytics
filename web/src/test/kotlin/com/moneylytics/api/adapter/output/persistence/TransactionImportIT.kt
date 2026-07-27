package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.service.TransactionImportService
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class TransactionImportIT : AbstractServiceIT() {
    @Autowired private lateinit var importService: TransactionImportService

    @Autowired private lateinit var transactionRepo: TransactionJpaRepository

    @Autowired private lateinit var accountRepo2: AccountJpaRepository

    private val date = LocalDate.of(2025, 1, 15)
    private val importIban = "DE00IMPORT999999999"

    @Test
    fun `should save all transactions and return count for fresh import`() {
        val command = command(tx("-100"), tx("-200"), tx("-50"))

        val count = importService.importTransactions(command)

        assertThat(count).isEqualTo(3)
        flushAndClear()
        assertThat(transactionRepo.findByOrganizationIdAndAccountingDateBetween(organizationId, date, date)).hasSize(3)
    }

    @Test
    fun `should return zero when re-importing the same transactions`() {
        val command = command(tx("-100"), tx("-200"), tx("-50"))
        importService.importTransactions(command)
        flushAndClear()

        val count = importService.importTransactions(command)

        assertThat(count).isEqualTo(0)
        assertThat(transactionRepo.findByOrganizationIdAndAccountingDateBetween(organizationId, date, date)).hasSize(3)
    }

    @Test
    fun `should save only new transactions when some already exist`() {
        val existing = tx("-100")
        importService.importTransactions(command(existing))
        flushAndClear()

        val count = importService.importTransactions(command(existing, tx("-200"), tx("-50")))

        assertThat(count).isEqualTo(2)
        assertThat(transactionRepo.findByOrganizationIdAndAccountingDateBetween(organizationId, date, date)).hasSize(3)
    }

    @Test
    fun `should save identical transactions in the same batch with different fingerprints`() {
        val sameTx = tx("-100")

        val count = importService.importTransactions(command(sameTx, sameTx))

        // Both get unique fingerprints (second gets raw:1 suffix) → both saved
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `should create account when IBAN is not yet known`() {
        val newIban = "DE00NEWIBAN00000001"
        val command =
            ImportTransactionsCommand(
                transactions =
                    listOf(
                        Transaction(
                            category = null,
                            subcategory = null,
                            bookingDate = date,
                            valueDate = date,
                            accountingDate = date,
                            amount = BigDecimal("-50"),
                            currency = "EUR",
                            accountIban = newIban,
                        ),
                    ),
                accountNames = mapOf(newIban to "New Account"),
                organizationId = organizationId,
            )

        importService.importTransactions(command)
        flushAndClear()

        assertThat(accountRepo2.findByIbanAndOrganizationId(newIban, organizationId)).isNotNull
    }

    @Test
    fun `should not create duplicate account when IBAN already known`() {
        val command = command(tx("-100"))
        importService.importTransactions(command)
        flushAndClear()

        importService.importTransactions(command)
        flushAndClear()

        val accounts = accountRepo2.findAllByOrganizationId(organizationId)
        assertThat(accounts.filter { it.iban == importIban }).hasSize(1)
    }

    private fun tx(amount: String) =
        Transaction(
            category = null,
            subcategory = null,
            bookingDate = date,
            valueDate = date,
            accountingDate = date,
            amount = BigDecimal(amount),
            currency = "EUR",
            accountIban = importIban,
        )

    private fun command(vararg transactions: Transaction) =
        ImportTransactionsCommand(
            transactions = transactions.toList(),
            accountNames = mapOf(importIban to "Import Account"),
            organizationId = organizationId,
        )
}
