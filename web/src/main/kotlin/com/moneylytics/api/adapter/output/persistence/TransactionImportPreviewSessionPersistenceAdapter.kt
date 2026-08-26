package com.moneylytics.api.adapter.output.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class TransactionImportPreviewSessionPersistenceAdapter(
    private val jpaRepository: TransactionImportPreviewSessionJpaRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    fun <T : Any> save(
        id: UUID,
        rows: List<T>,
        expiresAt: LocalDateTime,
    ) {
        val json = objectMapper.writeValueAsString(rows)
        jpaRepository.save(TransactionImportPreviewSessionEntity(id = id, rowsJson = json, expiresAt = expiresAt))
    }

    fun <T> load(
        id: UUID,
        rowType: Class<T>,
    ): List<T>? {
        val entity = jpaRepository.findById(id).orElse(null) ?: return null
        if (entity.expiresAt.isBefore(LocalDateTime.now())) {
            jpaRepository.delete(entity)
            return null
        }
        val listType = objectMapper.typeFactory.constructCollectionType(List::class.java, rowType)
        return objectMapper.readValue(entity.rowsJson, listType)
    }

    @Transactional
    fun delete(id: UUID) = jpaRepository.deleteById(id)

    @Transactional
    fun deleteExpired() = jpaRepository.deleteByExpiresAtBefore(LocalDateTime.now())
}
