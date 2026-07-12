package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Test
    fun `should find user by external id when user exists`() {
        val result = userRepo.findByExternalId("test-user-1")

        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo(user.id)
    }

    @Test
    fun `should return null when external id does not exist`() {
        val result = userRepo.findByExternalId("unknown-user")

        assertThat(result).isNull()
    }

    @Test
    fun `should distinguish between users with different external ids`() {
        val result1 = userRepo.findByExternalId("test-user-1")
        val result2 = userRepo.findByExternalId("test-user-2")

        assertThat(result1).isNotNull
        assertThat(result2).isNotNull
        assertThat(result1?.id).isNotEqualTo(result2?.id)
    }
}
