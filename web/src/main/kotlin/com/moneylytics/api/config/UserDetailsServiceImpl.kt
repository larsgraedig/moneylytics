package com.moneylytics.api.config

import com.moneylytics.api.application.port.output.UserRepository
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository,
) : ReactiveUserDetailsService {
    override fun findByUsername(username: String): Mono<UserDetails> =
        Mono
            .fromCallable { userRepository.findByExternalId(username) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { domainUser ->
                if (domainUser == null) return@flatMap Mono.empty()
                Mono.just<UserDetails>(
                    User
                        .withUsername(domainUser.externalId)
                        // OAuth users have no password — empty string never matches any BCrypt hash
                        .password(domainUser.passwordHash ?: "")
                        .roles("USER")
                        .build(),
                )
            }
}
