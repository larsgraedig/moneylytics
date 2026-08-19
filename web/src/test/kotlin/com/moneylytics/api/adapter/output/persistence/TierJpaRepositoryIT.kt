package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TierJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Test
    fun `should persist and find a tier`() {
        val saved = tierRepo.save(TierEntity(name = "Pro", isDefault = false))

        val found = tierRepo.findById(saved.id!!).orElse(null)

        assertThat(found).isNotNull
        assertThat(found.name).isEqualTo("Pro")
        assertThat(found.active).isTrue()
        assertThat(found.isDefault).isFalse()
    }

    @Test
    fun `should find the default tier`() {
        val found = tierRepo.findByIsDefaultTrue()

        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo("Standard")
        assertThat(found.isDefault).isTrue()
    }

    @Test
    fun `should clear all default tiers`() {
        tierRepo.clearDefault()
        flushAndClear()

        val found = tierRepo.findByIsDefaultTrue()

        assertThat(found).isNull()
    }
}
