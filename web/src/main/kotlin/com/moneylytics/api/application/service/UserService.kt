package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.CreateUserUseCase
import com.moneylytics.api.application.port.input.GetUserSettingsUseCase
import com.moneylytics.api.application.port.input.ListUsersUseCase
import com.moneylytics.api.application.port.input.LoginOAuthUserUseCase
import com.moneylytics.api.application.port.input.RegisterUserUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.UpdateUserSettingsUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.User
import com.moneylytics.api.domain.UserSettings
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class UserAlreadyExistsException(
    username: String,
) : RuntimeException("User '$username' already exists")

class InvalidEmailException(
    value: String,
) : RuntimeException("'$value' is not a valid email address")

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Service
class UserService(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val passwordEncoder: PasswordEncoder,
) : ResolveUserUseCase,
    ListUsersUseCase,
    CreateUserUseCase,
    RegisterUserUseCase,
    LoginOAuthUserUseCase,
    GetUserSettingsUseCase,
    UpdateUserSettingsUseCase {
    override fun listUsers(): List<User> = userRepository.findAll()

    override fun getSettings(userId: Long): UserSettings = userRepository.getSettings(userId)

    override fun updateSettings(
        userId: Long,
        organizationId: Long,
        defaultAccountIban: String?,
        language: String?,
        transactionsColumnOrder: List<String>?,
    ): UserSettings = userRepository.updateSettings(userId, organizationId, defaultAccountIban, language, transactionsColumnOrder)

    override fun resolveUser(externalId: String): Long =
        userRepository.findByExternalId(externalId)?.id
            ?: throw IllegalStateException("User '$externalId' not found")

    @Transactional
    override fun loginOAuthUser(externalId: String): Long {
        val existing = userRepository.findByExternalId(externalId)
        if (existing != null) return existing.id
        val user = userRepository.save(externalId, passwordHash = null)
        return user.id
    }

    @Transactional
    override fun registerUser(
        externalId: String,
        rawPassword: String,
    ): Long {
        val normalized = externalId.trim().lowercase()
        if (!EMAIL_REGEX.matches(normalized)) throw InvalidEmailException(normalized)
        if (userRepository.findByExternalId(normalized) != null) throw UserAlreadyExistsException(normalized)
        val passwordHash = passwordEncoder.encode(rawPassword)
        val user = userRepository.save(normalized, passwordHash)
        return user.id
    }

    @Transactional
    override fun createUser(
        externalId: String,
        rawPassword: String,
    ): Long {
        val passwordHash = passwordEncoder.encode(rawPassword)
        val user = userRepository.save(externalId, passwordHash)

        return user.id
    }
}
