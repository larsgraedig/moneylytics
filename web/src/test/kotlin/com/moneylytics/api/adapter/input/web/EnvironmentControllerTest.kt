package com.moneylytics.api.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EnvironmentControllerTest {
    private val controller = EnvironmentController()

    @Test
    fun `should return system environment variables sorted by key`() {
        val result = controller.environment()

        assertThat(result).isEqualTo(System.getenv().toSortedMap())
        assertThat(result.keys.toList()).isEqualTo(result.keys.sorted())
    }
}
