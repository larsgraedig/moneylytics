package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteThresholdUseCase
import com.moneylytics.api.application.port.input.GetThresholdStatusUseCase
import com.moneylytics.api.application.port.input.GetThresholdsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.User
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal

class ThresholdControllerTest {
    private val organizationId = 1L
    private val exchange: ServerWebExchange = mock()
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase = ResolveOrganizationUseCase { _, _ -> organizationId }
    private val getThresholdsUseCase: GetThresholdsUseCase = mock()
    private val saveThresholdUseCase: SaveThresholdUseCase = mock()
    private val deleteThresholdUseCase: DeleteThresholdUseCase = mock()
    private val getThresholdStatusUseCase: GetThresholdStatusUseCase = mock()
    private val controller =
        ThresholdController(
            getThresholdsUseCase,
            saveThresholdUseCase,
            deleteThresholdUseCase,
            getThresholdStatusUseCase,
            resolveOrganizationUseCase,
        )
    private val principal =
        User
            .withUsername("user@test.de")
            .password("x")
            .roles("USER")
            .build()
    private val stubThreshold =
        Threshold(
            id = 1L,
            category = "Test",
            subcategory = null,
            period = ThresholdPeriod.MONTHLY,
            notice = null,
            warning = null,
            critical = BigDecimal("100"),
        )

    @Test
    fun `should convert blank subcategory to null in toDomain`() =
        runTest {
            whenever(saveThresholdUseCase.saveThreshold(any(), any())).thenReturn(stubThreshold)
            val request =
                SaveThresholdRequest(
                    category = "Lebensmittel",
                    subcategory = "  ",
                    period = ThresholdPeriod.MONTHLY,
                    notice = null,
                    warning = null,
                    critical = BigDecimal("100"),
                )

            controller.saveThreshold(request, principal, exchange)

            val captor = argumentCaptor<Threshold>()
            org.mockito.kotlin
                .verify(saveThresholdUseCase)
                .saveThreshold(captor.capture(), any())
            assertThat(captor.firstValue.subcategory).isNull()
        }

    @Test
    fun `should convert blank group to null in toDomain`() =
        runTest {
            whenever(saveThresholdUseCase.saveThreshold(any(), any())).thenReturn(stubThreshold)
            val request =
                SaveThresholdRequest(
                    category = "Transport",
                    subcategory = null,
                    group = "  ",
                    period = ThresholdPeriod.MONTHLY,
                    notice = null,
                    warning = null,
                    critical = BigDecimal("50"),
                )

            controller.saveThreshold(request, principal, exchange)

            val captor = argumentCaptor<Threshold>()
            org.mockito.kotlin
                .verify(saveThresholdUseCase)
                .saveThreshold(captor.capture(), any())
            assertThat(captor.firstValue.group).isNull()
        }

    @Test
    fun `should preserve non-blank subcategory in toDomain`() =
        runTest {
            whenever(saveThresholdUseCase.saveThreshold(any(), any())).thenReturn(stubThreshold)
            val request =
                SaveThresholdRequest(
                    category = "Lebensmittel",
                    subcategory = "Supermarkt",
                    period = ThresholdPeriod.MONTHLY,
                    notice = null,
                    warning = null,
                    critical = BigDecimal("100"),
                )

            controller.saveThreshold(request, principal, exchange)

            val captor = argumentCaptor<Threshold>()
            org.mockito.kotlin
                .verify(saveThresholdUseCase)
                .saveThreshold(captor.capture(), any())
            assertThat(captor.firstValue.subcategory).isEqualTo("Supermarkt")
        }

    @Test
    fun `should map threshold entity to DTO including all levels`() =
        runTest {
            val threshold =
                Threshold(
                    id = 5L,
                    category = "Lebensmittel",
                    subcategory = "Supermarkt",
                    group = "Konsum",
                    period = ThresholdPeriod.WEEKLY,
                    notice = BigDecimal("80"),
                    warning = BigDecimal("120"),
                    critical = BigDecimal("200"),
                )
            whenever(getThresholdsUseCase.getThresholds(organizationId)).thenReturn(listOf(threshold))

            val response = controller.listThresholds(principal, exchange)

            assertThat(response.thresholds).hasSize(1)
            val dto = response.thresholds[0]
            assertThat(dto.id).isEqualTo(5L)
            assertThat(dto.category).isEqualTo("Lebensmittel")
            assertThat(dto.subcategory).isEqualTo("Supermarkt")
            assertThat(dto.group).isEqualTo("Konsum")
            assertThat(dto.period).isEqualTo(ThresholdPeriod.WEEKLY)
            assertThat(dto.notice).isEqualByComparingTo(BigDecimal("80"))
            assertThat(dto.warning).isEqualByComparingTo(BigDecimal("120"))
            assertThat(dto.critical).isEqualByComparingTo(BigDecimal("200"))
        }

    @Test
    fun `should return 204 on deleteThreshold`() =
        runTest {
            val response = controller.deleteThreshold(3L, principal, exchange)

            assertThat(response.statusCode.value()).isEqualTo(204)
            org.mockito.kotlin
                .verify(deleteThresholdUseCase)
                .deleteThreshold(3L, organizationId)
        }
}
