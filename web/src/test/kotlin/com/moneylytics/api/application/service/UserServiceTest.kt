package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

class UserServiceTest {
    private val userRepository: UserRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val passwordEncoder: PasswordEncoder = mock()
    private val service = UserService(userRepository, categoryRepository, passwordEncoder)

    @Test
    fun `should return existing user id on loginOAuthUser when user already exists`() {
        val existing = User(id = 42L, externalId = "oauth|123", passwordHash = null)
        whenever(userRepository.findByExternalId("oauth|123")).thenReturn(existing)

        val result = service.loginOAuthUser("oauth|123")

        assertThat(result).isEqualTo(42L)
    }

    @Test
    fun `should create and return new user id when user not found on loginOAuthUser`() {
        val newUser = User(id = 99L, externalId = "oauth|new", passwordHash = null)
        whenever(userRepository.findByExternalId("oauth|new")).thenReturn(null)
        whenever(userRepository.save("oauth|new", passwordHash = null)).thenReturn(newUser)

        val result = service.loginOAuthUser("oauth|new")

        assertThat(result).isEqualTo(99L)
    }

    @Test
    fun `should throw UserAlreadyExistsException when registering duplicate user`() {
        val existing = User(id = 1L, externalId = "user@test.de", passwordHash = "hash")
        whenever(userRepository.findByExternalId("user@test.de")).thenReturn(existing)

        assertThatThrownBy { service.registerUser("user@test.de", "secret") }
            .isInstanceOf(UserAlreadyExistsException::class.java)
    }

    @Test
    fun `should register new user with encoded password`() {
        val newUser = User(id = 5L, externalId = "new@test.de", passwordHash = "encoded")
        whenever(userRepository.findByExternalId("new@test.de")).thenReturn(null)
        whenever(passwordEncoder.encode("rawPass")).thenReturn("encoded")
        whenever(userRepository.save("new@test.de", "encoded")).thenReturn(newUser)

        val result = service.registerUser("new@test.de", "rawPass")

        assertThat(result).isEqualTo(5L)
        verify(passwordEncoder).encode("rawPass")
    }

    @Test
    fun `should resolve user id by external id when user exists`() {
        val user = User(id = 7L, externalId = "oauth|abc", passwordHash = null)
        whenever(userRepository.findByExternalId("oauth|abc")).thenReturn(user)

        val result = service.resolveUser("oauth|abc")

        assertThat(result).isEqualTo(7L)
    }

    @Test
    fun `should throw when resolving unknown external id`() {
        whenever(userRepository.findByExternalId("unknown")).thenReturn(null)

        assertThatThrownBy { service.resolveUser("unknown") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("unknown")
    }

    @Test
    fun `should create user with encoded password`() {
        val newUser = User(id = 8L, externalId = "admin", passwordHash = "hash")
        whenever(passwordEncoder.encode("pass")).thenReturn("hash")
        whenever(userRepository.save("admin", "hash")).thenReturn(newUser)

        val result = service.createUser("admin", "pass")

        assertThat(result).isEqualTo(8L)
    }
}
