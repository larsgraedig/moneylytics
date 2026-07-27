package com.moneylytics.api.adapter.input.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.moneylytics.api.application.port.input.GetOrganizationsUseCase
import com.moneylytics.api.application.port.input.RegisterUserUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.service.SESSION_KEY_ACTIVE_ORG
import com.moneylytics.api.application.service.UserAlreadyExistsException
import com.moneylytics.api.config.ImpersonationWebFilter.Companion.IMPERSONATED_USER_ID_KEY
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authManager: ReactiveAuthenticationManager,
    private val securityContextRepository: ServerSecurityContextRepository,
    private val registerUserUseCase: RegisterUserUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
    private val getOrganizationsUseCase: GetOrganizationsUseCase,
) {
    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_CONFLICT = 409
    }

    private val logger = KotlinLogging.logger {}

    private fun Collection<GrantedAuthority>.isSystemAdmin() = any { it.authority == "ROLE_SYSTEM_ADMIN" }

    @PostMapping("/login")
    suspend fun login(
        @RequestBody request: LoginRequest,
        exchange: ServerWebExchange,
    ): ResponseEntity<AuthResponse> {
        val token = UsernamePasswordAuthenticationToken(request.username, request.password)
        return try {
            val auth = authManager.authenticate(token).awaitSingle()
            securityContextRepository.save(exchange, SecurityContextImpl(auth)).awaitFirstOrNull()
            val response = withContext(Dispatchers.IO) { buildAuthResponse(auth.name, auth.authorities.isSystemAdmin()) }
            ResponseEntity.ok(response)
        } catch (e: AuthenticationException) {
            logger.debug(e) { "Authentication failed for user ${request.username}" }
            ResponseEntity.status(HTTP_UNAUTHORIZED).build()
        }
    }

    @GetMapping("/me")
    suspend fun me(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): AuthResponse {
        val session = exchange.session.awaitSingle()
        val activeOrgId = session.attributes[SESSION_KEY_ACTIVE_ORG] as? Long
        val impersonating = session.attributes[IMPERSONATED_USER_ID_KEY] as? String
        return withContext(Dispatchers.IO) {
            buildAuthResponse(
                username = principal.username,
                isSystemAdmin = principal.authorities.isSystemAdmin(),
                activeOrganizationId = activeOrgId,
                impersonating = impersonating,
            )
        }
    }

    @PostMapping("/register")
    suspend fun register(
        @RequestBody request: RegisterRequest,
        exchange: ServerWebExchange,
    ): ResponseEntity<AuthResponse> =
        try {
            withContext(Dispatchers.IO) { registerUserUseCase.registerUser(request.username, request.password) }
            val token = UsernamePasswordAuthenticationToken(request.username, request.password)
            val auth = authManager.authenticate(token).awaitSingle()
            securityContextRepository.save(exchange, SecurityContextImpl(auth)).awaitFirstOrNull()
            val response = withContext(Dispatchers.IO) { buildAuthResponse(auth.name, auth.authorities.isSystemAdmin()) }
            ResponseEntity.ok(response)
        } catch (e: UserAlreadyExistsException) {
            logger.debug(e) { "Registration failed: user ${request.username} already exists" }
            ResponseEntity.status(HTTP_CONFLICT).build()
        }

    @PostMapping("/logout")
    suspend fun logout(exchange: ServerWebExchange): ResponseEntity<Void> {
        exchange.session.awaitSingle().invalidate()
        return ResponseEntity.noContent().build()
    }

    private fun buildAuthResponse(
        username: String,
        isSystemAdmin: Boolean,
        activeOrganizationId: Long? = null,
        impersonating: String? = null,
    ): AuthResponse {
        val userId = resolveUserUseCase.resolveUser(username)
        val memberships = getOrganizationsUseCase.getOrganizations(userId)
        val orgs = memberships.map { OrganizationInfo(id = it.organization.id, name = it.organization.name, role = it.role.name) }
        val activeOrgId = activeOrganizationId ?: orgs.firstOrNull()?.id
        return AuthResponse(
            username = username,
            isSystemAdmin = isSystemAdmin,
            activeOrganizationId = activeOrgId,
            organizations = orgs,
            impersonating = impersonating,
        )
    }
}

data class LoginRequest(
    val username: String,
    val password: String,
)

data class RegisterRequest(
    val username: String,
    val password: String,
)

data class OrganizationInfo(
    val id: Long,
    val name: String,
    val role: String,
)

data class AuthResponse(
    val username: String,
    @JsonProperty("isSystemAdmin") val isSystemAdmin: Boolean = false,
    val activeOrganizationId: Long? = null,
    val organizations: List<OrganizationInfo> = emptyList(),
    val impersonating: String? = null,
)
