package com.moneylytics.api.config

import org.springframework.session.ReactiveSessionRepository
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Suppress("UNCHECKED_CAST")
internal class ReactiveJdbcSessionRepositoryAdapter(
    private val delegate: JdbcIndexedSessionRepository,
) : ReactiveSessionRepository<Session> {
    override fun createSession(): Mono<Session> =
        Mono
            .fromCallable { delegate.createSession() as Session }
            .subscribeOn(Schedulers.boundedElastic())

    override fun save(session: Session): Mono<Void> =
        Mono
            .fromRunnable<Void> { (delegate as SessionRepository<Session>).save(session) }
            .subscribeOn(Schedulers.boundedElastic())

    override fun findById(id: String): Mono<Session> =
        Mono
            .fromCallable { delegate.findById(id) as Session? }
            .subscribeOn(Schedulers.boundedElastic())

    override fun deleteById(id: String): Mono<Void> =
        Mono
            .fromRunnable<Void> { delegate.deleteById(id) }
            .subscribeOn(Schedulers.boundedElastic())
}
