package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CsvProfileJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var csvProfileRepo: CsvProfileJpaRepository

    @Test
    fun `should find csv profile by user id and fingerprint`() {
        val profile =
            csvProfileRepo.save(
                CsvProfileEntity(
                    organizationId = organizationId,
                    fingerprint = "csv-fp-abc123",
                    mappingJson = """{"dateColumn":"Datum"}""",
                ),
            )

        val result = csvProfileRepo.findByOrganizationIdAndFingerprint(organizationId, "csv-fp-abc123")

        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(profile.id)
        assertThat(result?.mappingJson).isEqualTo("""{"dateColumn":"Datum"}""")
    }

    @Test
    fun `should return null when fingerprint does not exist`() {
        csvProfileRepo.save(
            CsvProfileEntity(organizationId = organizationId, fingerprint = "csv-fp-abc123", mappingJson = "{}"),
        )

        val result = csvProfileRepo.findByOrganizationIdAndFingerprint(organizationId, "csv-fp-unknown")

        assertThat(result).isNull()
    }

    @Test
    fun `should return null when profile belongs to different user`() {
        csvProfileRepo.save(
            CsvProfileEntity(organizationId = otherOrganizationId, fingerprint = "csv-fp-abc123", mappingJson = "{}"),
        )

        val result = csvProfileRepo.findByOrganizationIdAndFingerprint(organizationId, "csv-fp-abc123")

        assertThat(result).isNull()
    }

    @Test
    fun `should allow same fingerprint for different users`() {
        csvProfileRepo.save(
            CsvProfileEntity(organizationId = organizationId, fingerprint = "csv-fp-shared", mappingJson = """{"dateColumn":"A"}"""),
        )
        csvProfileRepo.save(
            CsvProfileEntity(organizationId = otherOrganizationId, fingerprint = "csv-fp-shared", mappingJson = """{"dateColumn":"B"}"""),
        )

        val result = csvProfileRepo.findByOrganizationIdAndFingerprint(organizationId, "csv-fp-shared")

        assertThat(result).isNotNull
        assertThat(result?.mappingJson).isEqualTo("""{"dateColumn":"A"}""")
    }
}
