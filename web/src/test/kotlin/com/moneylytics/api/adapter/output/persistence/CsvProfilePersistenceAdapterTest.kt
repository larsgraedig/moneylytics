package com.moneylytics.api.adapter.output.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.moneylytics.api.adapter.input.web.AmountFormat
import com.moneylytics.api.adapter.input.web.GenericCsvMapping
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CsvProfilePersistenceAdapterTest {
    private val jpaRepository: CsvProfileJpaRepository = mock()
    private val adapter = CsvProfilePersistenceAdapter(jpaRepository)

    private val userId = 1L
    private val fingerprint = "abc123"
    private val mapping =
        GenericCsvMapping(
            delimiter = ";",
            dateColumn = "Datum",
            dateFormat = "dd.MM.yyyy",
            amountColumn = "Betrag",
            amountFormat = AmountFormat.GERMAN,
            purposeColumn = null,
            categoryColumn = null,
            subcategoryColumn = null,
            accountIbanColumn = null,
            currencyColumn = null,
            fixedAccountIban = "DE00TEST",
            fixedCurrency = "EUR",
        )
    private val mappingJson = jacksonObjectMapper().writeValueAsString(mapping)

    @Test
    fun `should return null when profile not found`() {
        whenever(jpaRepository.findByUserIdAndFingerprint(userId, fingerprint)).thenReturn(null)

        val result = adapter.findMapping(userId, fingerprint)

        assertThat(result).isNull()
    }

    @Test
    fun `should return null on JSON parse error`() {
        val entity = CsvProfileEntity(userId = userId, fingerprint = fingerprint, mappingJson = "not-valid-json", id = 1L)
        whenever(jpaRepository.findByUserIdAndFingerprint(userId, fingerprint)).thenReturn(entity)

        val result = adapter.findMapping(userId, fingerprint)

        assertThat(result).isNull()
    }

    @Test
    fun `should return parsed GenericCsvMapping when found`() {
        val entity = CsvProfileEntity(userId = userId, fingerprint = fingerprint, mappingJson = mappingJson, id = 1L)
        whenever(jpaRepository.findByUserIdAndFingerprint(userId, fingerprint)).thenReturn(entity)

        val result = adapter.findMapping(userId, fingerprint)

        assertThat(result).isNotNull
        assertThat(result!!.delimiter).isEqualTo(";")
        assertThat(result.dateColumn).isEqualTo("Datum")
        assertThat(result.fixedAccountIban).isEqualTo("DE00TEST")
    }

    @Test
    fun `should create new profile when not existing in saveMapping`() {
        whenever(jpaRepository.findByUserIdAndFingerprint(userId, fingerprint)).thenReturn(null)

        adapter.saveMapping(userId, fingerprint, mapping)

        verify(jpaRepository).save(any<CsvProfileEntity>())
    }

    @Test
    fun `should update existing profile JSON in saveMapping`() {
        val existing = CsvProfileEntity(userId = userId, fingerprint = fingerprint, mappingJson = "{}", id = 1L)
        whenever(jpaRepository.findByUserIdAndFingerprint(userId, fingerprint)).thenReturn(existing)

        val updatedMapping = mapping.copy(delimiter = ",")
        adapter.saveMapping(userId, fingerprint, updatedMapping)

        assertThat(existing.mappingJson).contains("\"delimiter\":\",\"")
        verify(jpaRepository).save(existing)
    }
}
