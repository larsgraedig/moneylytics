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

    private val organizationId = 1L
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
            groupColumn = null,
            accountIbanColumn = null,
            currencyColumn = null,
            fixedAccountIban = "DE00TEST",
            fixedCurrency = "EUR",
        )
    private val mappingJson = jacksonObjectMapper().writeValueAsString(mapping)

    @Test
    fun `should return null when profile not found`() {
        whenever(jpaRepository.findByOrganizationIdAndFingerprint(organizationId, fingerprint)).thenReturn(null)

        val result = adapter.findMapping(organizationId, fingerprint)

        assertThat(result).isNull()
    }

    @Test
    fun `should return null on JSON parse error`() {
        val entity = CsvProfileEntity(organizationId = organizationId, fingerprint = fingerprint, mappingJson = "not-valid-json", id = 1L)
        whenever(jpaRepository.findByOrganizationIdAndFingerprint(organizationId, fingerprint)).thenReturn(entity)

        val result = adapter.findMapping(organizationId, fingerprint)

        assertThat(result).isNull()
    }

    @Test
    fun `should return parsed GenericCsvMapping when found`() {
        val entity = CsvProfileEntity(organizationId = organizationId, fingerprint = fingerprint, mappingJson = mappingJson, id = 1L)
        whenever(jpaRepository.findByOrganizationIdAndFingerprint(organizationId, fingerprint)).thenReturn(entity)

        val result = adapter.findMapping(organizationId, fingerprint)

        assertThat(result).isNotNull
        assertThat(result!!.delimiter).isEqualTo(";")
        assertThat(result.dateColumn).isEqualTo("Datum")
        assertThat(result.fixedAccountIban).isEqualTo("DE00TEST")
    }

    @Test
    fun `should create new profile when not existing in saveMapping`() {
        whenever(jpaRepository.findByOrganizationIdAndFingerprint(organizationId, fingerprint)).thenReturn(null)

        adapter.saveMapping(organizationId, fingerprint, mapping)

        verify(jpaRepository).save(any<CsvProfileEntity>())
    }

    @Test
    fun `should update existing profile JSON in saveMapping`() {
        val existing = CsvProfileEntity(organizationId = organizationId, fingerprint = fingerprint, mappingJson = "{}", id = 1L)
        whenever(jpaRepository.findByOrganizationIdAndFingerprint(organizationId, fingerprint)).thenReturn(existing)

        val updatedMapping = mapping.copy(delimiter = ",")
        adapter.saveMapping(organizationId, fingerprint, updatedMapping)

        assertThat(existing.mappingJson).contains("\"delimiter\":\",\"")
        verify(jpaRepository).save(existing)
    }
}
