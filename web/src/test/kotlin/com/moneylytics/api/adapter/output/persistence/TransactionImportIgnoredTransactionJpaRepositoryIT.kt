package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TransactionImportIgnoredTransactionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var ignoredRepo: TransactionImportIgnoredTransactionJpaRepository

    @Test
    fun `should return fingerprints that exist for user`() {
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-a", organization = organization))
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-b", organization = organization))

        val result =
            ignoredRepo.findExistingFingerprints(
                fingerprints = listOf("fp-a", "fp-b", "fp-unknown"),
                organizationId = organizationId,
            )

        assertThat(result).containsExactlyInAnyOrder("fp-a", "fp-b")
    }

    @Test
    fun `should not return fingerprints belonging to other user`() {
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-mine", organization = organization))
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-theirs", organization = otherOrganization))

        val result =
            ignoredRepo.findExistingFingerprints(
                fingerprints = listOf("fp-mine", "fp-theirs"),
                organizationId = organizationId,
            )

        assertThat(result).containsExactly("fp-mine")
    }

    @Test
    fun `should return empty list when no fingerprints match`() {
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-stored", organization = organization))

        val result =
            ignoredRepo.findExistingFingerprints(
                fingerprints = listOf("fp-not-stored"),
                organizationId = organizationId,
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `should delete ignored transactions by fingerprints and user id`() {
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-delete", organization = organization))
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-keep", organization = organization))
        flushAndClear()

        ignoredRepo.deleteByFingerprintInAndOrganizationId(listOf("fp-delete"), organizationId)
        flushAndClear()

        val remaining = ignoredRepo.findExistingFingerprints(listOf("fp-delete", "fp-keep"), organizationId)
        assertThat(remaining).containsExactly("fp-keep")
    }

    @Test
    fun `should not delete ignored transactions belonging to other user`() {
        ignoredRepo.save(TransactionImportIgnoredTransactionEntity(fingerprint = "fp-theirs", organization = otherOrganization))
        flushAndClear()

        ignoredRepo.deleteByFingerprintInAndOrganizationId(listOf("fp-theirs"), organizationId)
        flushAndClear()

        val remaining = ignoredRepo.findExistingFingerprints(listOf("fp-theirs"), otherOrganizationId)
        assertThat(remaining).containsExactly("fp-theirs")
    }
}
