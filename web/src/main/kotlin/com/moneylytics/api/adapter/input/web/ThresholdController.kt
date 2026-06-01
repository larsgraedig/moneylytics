package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.DeleteThresholdUseCase
import com.moneylytics.api.application.port.input.GetThresholdsUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/thresholds")
class ThresholdController(
    private val getThresholdsUseCase: GetThresholdsUseCase,
    private val saveThresholdUseCase: SaveThresholdUseCase,
    private val deleteThresholdUseCase: DeleteThresholdUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping
    suspend fun listThresholds(
        @RequestHeader("X-User-Id") externalId: String,
    ): ThresholdsResponse =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(externalId)
            ThresholdsResponse(getThresholdsUseCase.getThresholds(userId).map { it.toDto() })
        }

    @PutMapping
    suspend fun saveThreshold(
        @RequestBody request: SaveThresholdRequest,
        @RequestHeader("X-User-Id") externalId: String,
    ): ThresholdDto =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(externalId)
            saveThresholdUseCase.saveThreshold(request.toDomain(), userId).toDto()
        }

    @DeleteMapping("/{id}")
    suspend fun deleteThreshold(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") externalId: String,
    ): ResponseEntity<Unit> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(externalId)
            deleteThresholdUseCase.deleteThreshold(id, userId)
            ResponseEntity.noContent().build()
        }

    private fun Threshold.toDto() =
        ThresholdDto(
            id = id,
            category = category,
            subcategory = subcategory,
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
    val period: ThresholdPeriod,
    val notice: BigDecimal?,
    val warning: BigDecimal?,
    val critical: BigDecimal?,
)

data class SaveThresholdRequest(
    val category: String,
    val subcategory: String?,
    val period: ThresholdPeriod,
    val notice: BigDecimal?,
    val warning: BigDecimal?,
    val critical: BigDecimal?,
)
