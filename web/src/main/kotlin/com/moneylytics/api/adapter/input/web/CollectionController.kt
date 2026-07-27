package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CreateCollectionUseCase
import com.moneylytics.api.application.port.input.DeleteCollectionUseCase
import com.moneylytics.api.application.port.input.GetCollectionsUseCase
import com.moneylytics.api.application.port.input.ManageCollectionMembersUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.input.UpdateCollectionUseCase
import com.moneylytics.api.domain.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/collections")
class CollectionController(
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val updateCollectionUseCase: UpdateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val manageCollectionMembersUseCase: ManageCollectionMembersUseCase,
    private val resolveOrganizationUseCase: ResolveOrganizationUseCase,
) {
    @GetMapping
    suspend fun listCollections(
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CollectionsResponse {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            CollectionsResponse(
                getCollectionsUseCase.getCollections(organizationId).map { c ->
                    CollectionDto(
                        id = c.id,
                        name = c.name,
                        note = c.note,
                        transactions = c.transactions.map { it.toItem() },
                    )
                },
            )
        }
    }

    @GetMapping("/{id}")
    suspend fun getCollection(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<CollectionDto> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            val collection =
                getCollectionsUseCase.getCollection(id, organizationId)
                    ?: return@withContext ResponseEntity.notFound().build()
            ResponseEntity.ok(
                CollectionDto(
                    id = collection.id,
                    name = collection.name,
                    note = collection.note,
                    transactions = collection.transactions.map { it.toItem() },
                ),
            )
        }
    }

    @PostMapping
    suspend fun createCollection(
        @RequestBody request: CreateCollectionRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CollectionDto {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            createCollectionUseCase
                .createCollection(Collection(name = request.name, note = request.note), organizationId)
                .toEmptyDto()
        }
    }

    @PutMapping("/{id}")
    suspend fun updateCollection(
        @PathVariable id: Long,
        @RequestBody request: UpdateCollectionRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): CollectionDto {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        return withContext(Dispatchers.IO) {
            updateCollectionUseCase
                .updateCollection(Collection(id = id, name = request.name, note = request.note), organizationId)
                .toEmptyDto()
        }
    }

    @DeleteMapping("/{id}")
    suspend fun deleteCollection(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Unit> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            deleteCollectionUseCase.deleteCollection(id, organizationId)
        }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/transactions")
    suspend fun addTransaction(
        @PathVariable id: Long,
        @RequestBody request: CollectionTransactionRequest,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Unit> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            manageCollectionMembersUseCase.addTransaction(id, request.transactionId, organizationId)
        }
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/transactions/{transactionId}")
    suspend fun removeTransaction(
        @PathVariable id: Long,
        @PathVariable transactionId: Long,
        @AuthenticationPrincipal principal: UserDetails,
        exchange: ServerWebExchange,
    ): ResponseEntity<Unit> {
        val organizationId = resolveOrganizationUseCase.resolveOrganization(principal, exchange)
        withContext(Dispatchers.IO) {
            manageCollectionMembersUseCase.removeTransaction(id, transactionId, organizationId)
        }
        return ResponseEntity.noContent().build()
    }

    private fun Collection.toEmptyDto() =
        CollectionDto(
            id = requireNotNull(id),
            name = name,
            note = note,
            transactions = emptyList(),
        )
}

data class CollectionsResponse(
    val collections: List<CollectionDto>,
)

data class CollectionDto(
    val id: Long,
    val name: String,
    val note: String?,
    val transactions: List<TransactionItem>,
)

data class CreateCollectionRequest(
    val name: String,
    val note: String? = null,
)

data class UpdateCollectionRequest(
    val name: String,
    val note: String? = null,
)

data class CollectionTransactionRequest(
    val transactionId: Long,
)
