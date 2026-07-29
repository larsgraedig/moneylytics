package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteThresholdUseCase
import com.moneylytics.api.application.port.input.GetThresholdStatusQuery
import com.moneylytics.api.application.port.input.GetThresholdStatusUseCase
import com.moneylytics.api.application.port.input.GetThresholdsUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.application.port.input.ThresholdStatusResponse
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/thresholds")
class ThresholdController(
    private val getThresholdsUseCase: GetThresholdsUseCase,
    private val saveThresholdUseCase: SaveThresholdUseCase,
    private val deleteThresholdUseCase: DeleteThresholdUseCase,
    private val getThresholdStatusUseCase: GetThresholdStatusUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @GetMapping
    suspend fun listThresholds(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ThresholdsResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            ThresholdsResponse(getThresholdsUseCase.getThresholds(organizationId).map { it.toDto() })
        }
    }

    @PutMapping
    suspend fun saveThreshold(
        @RequestBody request: SaveThresholdRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ThresholdDto {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            saveThresholdUseCase.saveThreshold(request.toDomain(), organizationId).toDto()
        }
    }

    @GetMapping("/status")
    suspend fun getThresholdStatus(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) iban: String? = null,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ThresholdStatusResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            getThresholdStatusUseCase.getThresholdStatus(
                GetThresholdStatusQuery(from = from, to = to, organizationId = organizationId, accountIban = iban),
            )
        }
    }

    @DeleteMapping("/{id}")
    suspend fun deleteThreshold(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Unit> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            deleteThresholdUseCase.deleteThreshold(id, organizationId)
        }
        return ResponseEntity.noContent().build()
    }

    private fun Threshold.toDto() =
        ThresholdDto(
            id = id,
            category = category,
            subcategory = subcategory,
            group = group,
            period = period,
            notice = notice,
            warning = warning,
            critical = critical,
        )

    private fun SaveThresholdRequest.toDomain() =
        Threshold(
            id = 0,
            category = category,
            subcategory = subcategory?.takeIf { it.isNotBlank() },
            group = group?.takeIf { it.isNotBlank() },
            period = period,
            notice = notice,
            warning = warning,
            critical = critical,
        )
}

data class ThresholdsResponse(
    val thresholds: List<ThresholdDto>,
)

data class ThresholdDto(
    val id: Long,
    val category: String,
    val subcategory: String?,
    val group: String? = null,
    val period: ThresholdPeriod,
    val notice: BigDecimal?,
    val warning: BigDecimal?,
    val critical: BigDecimal?,
)

data class SaveThresholdRequest(
    val category: String,
    val subcategory: String?,
    val group: String? = null,
    val period: ThresholdPeriod,
    val notice: BigDecimal?,
    val warning: BigDecimal?,
    val critical: BigDecimal?,
)
