package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.AccountRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Account
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class AccountQueryServiceTest {
    private val accountRepository: AccountRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val service = AccountQueryService(accountRepository, transactionRepository)

    private val userId = 1L

    @Test
    fun `should merge latest transaction dates into accounts`() {
        val giro = Account(iban = "DE01", name = "Giro")
        val sparkonto = Account(iban = "DE02", name = "Sparkonto")
        val latestDate = LocalDate.of(2025, 6, 15)
        whenever(transactionRepository.latestTransactionDatesByUserId(userId)).thenReturn(mapOf("DE01" to latestDate))
        whenever(accountRepository.findAll(userId)).thenReturn(listOf(giro, sparkonto))

        val result = service.getAccounts(userId)

        assertThat(result.first { it.iban == "DE01" }.latestTransactionDate).isEqualTo(latestDate)
        assertThat(result.first { it.iban == "DE02" }.latestTransactionDate).isNull()
    }

    @Test
    fun `should return accounts with null date when no transactions exist`() {
        val account = Account(iban = "DE01", name = "Giro")
        whenever(transactionRepository.latestTransactionDatesByUserId(userId)).thenReturn(emptyMap())
        whenever(accountRepository.findAll(userId)).thenReturn(listOf(account))

        val result = service.getAccounts(userId)

        assertThat(result).hasSize(1)
        assertThat(result[0].latestTransactionDate).isNull()
    }

    @Test
    fun `should save new account with given name`() {
        val saved = Account(iban = "DE01", name = "Giro")
        whenever(accountRepository.findByIban("DE01", userId)).thenReturn(null)
        whenever(accountRepository.save(Account(iban = "DE01", name = "Giro"), userId)).thenReturn(saved)

        val result = service.saveAccount("DE01", "Giro", userId)

        assertThat(result).isEqualTo(saved)
    }

    @Test
    fun `should use IBAN as name when name is blank`() {
        val saved = Account(iban = "DE01", name = "DE01")
        whenever(accountRepository.findByIban("DE01", userId)).thenReturn(null)
        whenever(accountRepository.save(Account(iban = "DE01", name = "DE01"), userId)).thenReturn(saved)

        val result = service.saveAccount("DE01", "   ", userId)

        assertThat(result.name).isEqualTo("DE01")
    }

    @Test
    fun `should delete account`() {
        service.deleteAccount("DE01", userId)

        verify(accountRepository).delete("DE01", userId)
    }
}
