package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.RegisterUserUseCase
import com.moneylytics.api.application.service.UserAlreadyExistsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
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
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    suspend fun login(
        @RequestBody request: LoginRequest,
        exchange: ServerWebExchange,
    ): ResponseEntity<AuthResponse> {
        val token = UsernamePasswordAuthenticationToken(request.username, request.password)
        return try {
            val auth = authManager.authenticate(token).awaitSingle()
            securityContextRepository.save(exchange, SecurityContextImpl(auth)).awaitFirstOrNull()
            ResponseEntity.ok(AuthResponse(username = auth.name))
        } catch (e: AuthenticationException) {
            logger.debug("Authentication failed for user ${request.username}", e)
            ResponseEntity.status(401).build()
        }
    }

    @GetMapping("/me")
    suspend fun me(
        @AuthenticationPrincipal principal: UserDetails,
    ): AuthResponse = AuthResponse(username = principal.username)

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
            ResponseEntity.ok(AuthResponse(username = auth.name))
        } catch (e: UserAlreadyExistsException) {
            logger.debug("Registration failed: user ${request.username} already exists", e)
            ResponseEntity.status(409).build()
        }

    @PostMapping("/logout")
    suspend fun logout(exchange: ServerWebExchange): ResponseEntity<Void> {
        exchange.session.awaitSingle().invalidate()
        return ResponseEntity.noContent().build()
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

data class AuthResponse(
    val username: String,
)
