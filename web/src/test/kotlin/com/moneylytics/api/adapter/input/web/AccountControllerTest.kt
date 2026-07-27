package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteAccountUseCase
import com.moneylytics.api.application.port.input.GetAccountsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.SaveAccountUseCase
import com.moneylytics.api.domain.Account
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ServerWebExchange
import java.time.LocalDate

class AccountControllerTest {
    private val organizationId = 1L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val getAccountsUseCase: GetAccountsUseCase = mock()
    private val saveAccountUseCase: SaveAccountUseCase = mock()
    private val deleteAccountUseCase: DeleteAccountUseCase = mock()
    private val controller = AccountController(getAccountsUseCase, saveAccountUseCase, deleteAccountUseCase, resolveOrganizationUseCase)
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()

    @Test
    fun `should return 400 when IBAN is blank on createAccount`() =
        runTest {
            val response = controller.createAccount(SaveAccountRequest(iban = "  ", name = "Giro"), principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            verify(saveAccountUseCase, never()).saveAccount(any(), any(), any())
        }

    @Test
    fun `should trim IBAN and name before passing to use case`() =
        runTest {
            val saved = Account(iban = "DE00TEST", name = "Giro")
            whenever(saveAccountUseCase.saveAccount("DE00TEST", "Giro", organizationId)).thenReturn(saved)

            val response = controller.createAccount(SaveAccountRequest(iban = " DE00TEST ", name = " Giro "), principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            verify(saveAccountUseCase).saveAccount("DE00TEST", "Giro", organizationId)
        }

    @Test
    fun `should map latestTransactionDate to string in getAccounts`() =
        runTest {
            val date = LocalDate.of(2025, 6, 15)
            whenever(getAccountsUseCase.getAccounts(organizationId)).thenReturn(
                listOf(
                    Account(iban = "DE01", name = "Giro", latestTransactionDate = date),
                    Account(iban = "DE02", name = "Sparkonto", latestTransactionDate = null),
                ),
            )

            val response = controller.getAccounts(principal, exchange)

            assertThat(response.accounts).hasSize(2)
            assertThat(response.accounts[0].lastTransactionDate).isEqualTo("2025-06-15")
            assertThat(response.accounts[1].lastTransactionDate).isNull()
        }

    @Test
    fun `should return 204 on deleteAccount`() =
        runTest {
            val response = controller.deleteAccount("DE00TEST", principal, exchange)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            verify(deleteAccountUseCase).deleteAccount("DE00TEST", organizationId)
        }
}
