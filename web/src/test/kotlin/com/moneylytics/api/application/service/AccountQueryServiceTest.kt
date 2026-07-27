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

    private val organizationId = 1L

    @Test
    fun `should merge latest transaction dates into accounts`() {
        val giro = Account(iban = "DE01", name = "Giro")
        val sparkonto = Account(iban = "DE02", name = "Sparkonto")
        val latestDate = LocalDate.of(2025, 6, 15)
        whenever(transactionRepository.latestTransactionDatesByOrganizationId(organizationId)).thenReturn(mapOf("DE01" to latestDate))
        whenever(accountRepository.findAll(organizationId)).thenReturn(listOf(giro, sparkonto))

        val result = service.getAccounts(organizationId)

        assertThat(result.first { it.iban == "DE01" }.latestTransactionDate).isEqualTo(latestDate)
        assertThat(result.first { it.iban == "DE02" }.latestTransactionDate).isNull()
    }

    @Test
    fun `should return accounts with null date when no transactions exist`() {
        val account = Account(iban = "DE01", name = "Giro")
        whenever(transactionRepository.latestTransactionDatesByOrganizationId(organizationId)).thenReturn(emptyMap())
        whenever(accountRepository.findAll(organizationId)).thenReturn(listOf(account))

        val result = service.getAccounts(organizationId)

        assertThat(result).hasSize(1)
        assertThat(result[0].latestTransactionDate).isNull()
    }

    @Test
    fun `should save new account with given name`() {
        val saved = Account(iban = "DE01", name = "Giro")
        whenever(accountRepository.findByIban("DE01", organizationId)).thenReturn(null)
        whenever(accountRepository.save(Account(iban = "DE01", name = "Giro"), organizationId)).thenReturn(saved)

        val result = service.saveAccount("DE01", "Giro", organizationId)

        assertThat(result).isEqualTo(saved)
    }

    @Test
    fun `should use IBAN as name when name is blank`() {
        val saved = Account(iban = "DE01", name = "DE01")
        whenever(accountRepository.findByIban("DE01", organizationId)).thenReturn(null)
        whenever(accountRepository.save(Account(iban = "DE01", name = "DE01"), organizationId)).thenReturn(saved)

        val result = service.saveAccount("DE01", "   ", organizationId)

        assertThat(result.name).isEqualTo("DE01")
    }

    @Test
    fun `should delete account`() {
        service.deleteAccount("DE01", organizationId)

        verify(accountRepository).delete("DE01", organizationId)
    }
}
