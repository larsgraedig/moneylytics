package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CreateVirtualTransactionCommand
import com.moneylytics.api.application.port.input.GetSubTransactionGroupUseCase
import com.moneylytics.api.application.port.input.ManageSubTransactionUseCase
import com.moneylytics.api.application.port.input.MergeTransactionsCommand
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.SplitTransactionCommand
import com.moneylytics.api.application.port.input.UpdateVirtualTransactionCommand
import com.moneylytics.api.domain.SplitItem
import com.moneylytics.api.domain.SubTransactionGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.math.BigDecimal
import java.time.LocalDate

data class SplitItemRequest(
    val amount: BigDecimal,
    val categoryId: Long? = null,
    val comment: String? = null,
)

data class SplitRequest(
    val splits: List<SplitItemRequest>,
)

data class MergeRequest(
    val transactionIds: List<Long>,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val accountingDate: LocalDate,
    val name: String? = null,
    val comment: String? = null,
)

data class UpdateVirtualTransactionRequest(
    val amount: BigDecimal,
    val accountIban: String,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val accountingDate: LocalDate,
    val categoryId: Long? = null,
    val counterpartyName: String? = null,
    val purpose: String? = null,
)

data class CreateVirtualTransactionRequest(
    val amount: BigDecimal,
    val currency: String = "EUR",
    val accountIban: String,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val accountingDate: LocalDate,
    val categoryId: Long? = null,
    val counterpartyName: String? = null,
    val purpose: String? = null,
)

data class SubTransactionGroupResponse(
    val parent: TransactionItem,
    val children: List<TransactionItem>,
)

private fun SubTransactionGroup.toResponse() =
    SubTransactionGroupResponse(
        parent = parent.toItem(),
        children = children.map { it.toItem() },
    )

@RestController
@RequestMapping("/transactions")
class SubTransactionController(
    private val manageSubTransactionUseCase: ManageSubTransactionUseCase,
    private val getSubTransactionGroupUseCase: GetSubTransactionGroupUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @PostMapping("/{id}/split")
    suspend fun splitTransaction(
        @PathVariable id: Long,
        @RequestBody request: SplitRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<SubTransactionGroupResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.splitTransaction(
                    SplitTransactionCommand(
                        transactionId = id,
                        splits = request.splits.map { SplitItem(it.amount, it.categoryId, it.comment) },
                        organizationId = organizationId,
                    ),
                )
            }.fold(
                onSuccess = { ResponseEntity.ok(it.toResponse()) },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        is NoSuchElementException -> ResponseEntity.notFound().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @DeleteMapping("/{id}/split")
    suspend fun unsplitTransaction(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.unsplitTransaction(id, organizationId)
            }.fold(
                onSuccess = { ResponseEntity.noContent().build() },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @PostMapping("/merge")
    suspend fun mergeTransactions(
        @RequestBody request: MergeRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<SubTransactionGroupResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.mergeTransactions(
                    MergeTransactionsCommand(
                        transactionIds = request.transactionIds,
                        accountingDate = request.accountingDate,
                        name = request.name,
                        comment = request.comment,
                        organizationId = organizationId,
                    ),
                )
            }.fold(
                onSuccess = { ResponseEntity.ok(it.toResponse()) },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @PostMapping("/{parentId}/merge/{transactionId}")
    suspend fun addToMerge(
        @PathVariable parentId: Long,
        @PathVariable transactionId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<SubTransactionGroupResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.addToMerge(parentId, transactionId, organizationId)
            }.fold(
                onSuccess = { ResponseEntity.ok(it.toResponse()) },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @DeleteMapping("/{parentId}/merge/{transactionId}")
    suspend fun removeFromMerge(
        @PathVariable transactionId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<SubTransactionGroupResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.removeFromMerge(transactionId, organizationId)
            }.fold(
                onSuccess = { ResponseEntity.ok(it.toResponse()) },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @DeleteMapping("/{parentId}/merge")
    suspend fun unmergeTransactions(
        @PathVariable parentId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.unmergeTransactions(parentId, organizationId)
            }.fold(
                onSuccess = { ResponseEntity.noContent().build() },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @PostMapping("/virtual")
    suspend fun createVirtualTransaction(
        @RequestBody request: CreateVirtualTransactionRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.createVirtualTransaction(
                    CreateVirtualTransactionCommand(
                        amount = request.amount,
                        currency = request.currency,
                        accountIban = request.accountIban,
                        accountingDate = request.accountingDate,
                        categoryId = request.categoryId,
                        counterpartyName = request.counterpartyName,
                        purpose = request.purpose,
                        organizationId = organizationId,
                    ),
                )
            }.fold(
                onSuccess = { ResponseEntity.ok(it.toItem()) },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        is NoSuchElementException -> ResponseEntity.notFound().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @PatchMapping("/{id}/virtual")
    suspend fun updateVirtualTransaction(
        @PathVariable id: Long,
        @RequestBody request: UpdateVirtualTransactionRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<TransactionItem> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.updateVirtualTransaction(
                    UpdateVirtualTransactionCommand(
                        transactionId = id,
                        amount = request.amount,
                        accountIban = request.accountIban,
                        accountingDate = request.accountingDate,
                        categoryId = request.categoryId,
                        counterpartyName = request.counterpartyName,
                        purpose = request.purpose,
                        organizationId = organizationId,
                    ),
                )
            }.fold(
                onSuccess = { ResponseEntity.ok(it.toItem()) },
                onFailure = { e ->
                    when (e) {
                        is NoSuchElementException -> ResponseEntity.notFound().build()
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is IllegalStateException -> ResponseEntity.badRequest().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @DeleteMapping("/{id}/virtual")
    suspend fun deleteVirtualTransaction(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Void> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            runCatching {
                manageSubTransactionUseCase.deleteVirtualTransaction(id, organizationId)
            }.fold(
                onSuccess = { ResponseEntity.noContent().build() },
                onFailure = { e ->
                    when (e) {
                        is IllegalArgumentException -> ResponseEntity.badRequest().build()
                        is NoSuchElementException -> ResponseEntity.notFound().build()
                        else -> throw e
                    }
                },
            )
        }
    }

    @GetMapping("/{id}/sub-group")
    suspend fun getSubGroup(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<SubTransactionGroupResponse> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val group = getSubTransactionGroupUseCase.getGroup(id, organizationId)
            if (group != null) ResponseEntity.ok(group.toResponse()) else ResponseEntity.notFound().build()
        }
    }
}
