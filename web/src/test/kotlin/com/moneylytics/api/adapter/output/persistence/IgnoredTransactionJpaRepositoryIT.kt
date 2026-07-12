package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class IgnoredTransactionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var ignoredRepo: IgnoredTransactionJpaRepository

    @Test
    fun `should return fingerprints that exist for user`() {
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-a", user = user))
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-b", user = user))

        val result =
            ignoredRepo.findExistingFingerprints(
                fingerprints = listOf("fp-a", "fp-b", "fp-unknown"),
                userId = userId,
            )

        assertThat(result).containsExactlyInAnyOrder("fp-a", "fp-b")
    }

    @Test
    fun `should not return fingerprints belonging to other user`() {
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-mine", user = user))
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-theirs", user = otherUser))

        val result =
            ignoredRepo.findExistingFingerprints(
                fingerprints = listOf("fp-mine", "fp-theirs"),
                userId = userId,
            )

        assertThat(result).containsExactly("fp-mine")
    }

    @Test
    fun `should return empty list when no fingerprints match`() {
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-stored", user = user))

        val result =
            ignoredRepo.findExistingFingerprints(
                fingerprints = listOf("fp-not-stored"),
                userId = userId,
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `should delete ignored transactions by fingerprints and user id`() {
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-delete", user = user))
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-keep", user = user))
        flushAndClear()

        ignoredRepo.deleteByFingerprintInAndUserId(listOf("fp-delete"), userId)
        flushAndClear()

        val remaining = ignoredRepo.findExistingFingerprints(listOf("fp-delete", "fp-keep"), userId)
        assertThat(remaining).containsExactly("fp-keep")
    }

    @Test
    fun `should not delete ignored transactions belonging to other user`() {
        ignoredRepo.save(IgnoredTransactionEntity(fingerprint = "fp-theirs", user = otherUser))
        flushAndClear()

        ignoredRepo.deleteByFingerprintInAndUserId(listOf("fp-theirs"), userId)
        flushAndClear()

        val remaining = ignoredRepo.findExistingFingerprints(listOf("fp-theirs"), otherUserId)
        assertThat(remaining).containsExactly("fp-theirs")
    }
}
