package com.moneylytics.api.adapter.output.persistence

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.moneylytics.api.adapter.input.web.GenericCsvMapping
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CsvProfilePersistenceAdapter(
    private val jpaRepository: CsvProfileJpaRepository,
) {
    private val objectMapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun findMapping(
        userId: Long,
        fingerprint: String,
    ): GenericCsvMapping? {
        val entity = jpaRepository.findByUserIdAndFingerprint(userId, fingerprint) ?: return null
        return runCatching { objectMapper.readValue(entity.mappingJson, GenericCsvMapping::class.java) }.getOrNull()
    }

    @Transactional
    fun saveMapping(
        userId: Long,
        fingerprint: String,
        mapping: GenericCsvMapping,
    ) {
        val json = objectMapper.writeValueAsString(mapping)
        val existing = jpaRepository.findByUserIdAndFingerprint(userId, fingerprint)
        if (existing != null) {
            existing.mappingJson = json
            jpaRepository.save(existing)
        } else {
            jpaRepository.save(CsvProfileEntity(userId = userId, fingerprint = fingerprint, mappingJson = json))
        }
    }
}
