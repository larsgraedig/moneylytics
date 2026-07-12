package com.moneylytics.api.config

import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.test.StepVerifier

class UserDetailsServiceImplTest {
    private val userRepository: UserRepository = mock()
    private val service = UserDetailsServiceImpl(userRepository)

    @Test
    fun `should return empty Mono when user not found`() {
        whenever(userRepository.findByExternalId("unknown@test.de")).thenReturn(null)

        StepVerifier.create(service.findByUsername("unknown@test.de")).verifyComplete()
    }

    @Test
    fun `should return UserDetails with password hash and USER role when user found`() {
        val domainUser = User(id = 1L, externalId = "user@test.de", passwordHash = "bcrypt-hash")
        whenever(userRepository.findByExternalId("user@test.de")).thenReturn(domainUser)

        StepVerifier
            .create(service.findByUsername("user@test.de"))
            .assertNext { details ->
                assertThat(details.username).isEqualTo("user@test.de")
                assertThat(details.password).isEqualTo("bcrypt-hash")
                assertThat(details.authorities.map { it.authority }).contains("ROLE_USER")
            }.verifyComplete()
    }

    @Test
    fun `should return UserDetails with empty password for OAuth user without password hash`() {
        val domainUser = User(id = 2L, externalId = "oauth|abc123", passwordHash = null)
        whenever(userRepository.findByExternalId("oauth|abc123")).thenReturn(domainUser)

        StepVerifier
            .create(service.findByUsername("oauth|abc123"))
            .assertNext { details ->
                assertThat(details.username).isEqualTo("oauth|abc123")
                assertThat(details.password).isEqualTo("")
            }.verifyComplete()
    }
}
