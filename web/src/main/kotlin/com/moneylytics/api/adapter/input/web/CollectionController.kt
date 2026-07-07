package com.moneylytics.api.adapter.input.web

import com.moneylytics.api.application.port.input.CreateCollectionUseCase
import com.moneylytics.api.application.port.input.DeleteCollectionUseCase
import com.moneylytics.api.application.port.input.GetCollectionsUseCase
import com.moneylytics.api.application.port.input.ManageCollectionMembersUseCase
import com.moneylytics.api.application.port.input.ResolveUserUseCase
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

@RestController
@RequestMapping("/collections")
class CollectionController(
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val updateCollectionUseCase: UpdateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val manageCollectionMembersUseCase: ManageCollectionMembersUseCase,
    private val resolveUserUseCase: ResolveUserUseCase,
) {
    @GetMapping
    suspend fun listCollections(
        @AuthenticationPrincipal principal: UserDetails,
    ): CollectionsResponse =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            CollectionsResponse(
                getCollectionsUseCase.getCollections(userId).map { c ->
                    CollectionDto(
                        id = c.id,
                        name = c.name,
                        note = c.note,
                        transactions = c.transactions.map { it.toItem() },
                    )
                },
            )
        }

    @PostMapping
    suspend fun createCollection(
        @RequestBody request: CreateCollectionRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): CollectionDto =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            createCollectionUseCase
                .createCollection(Collection(name = request.name, note = request.note), userId)
                .toEmptyDto()
        }

    @PutMapping("/{id}")
    suspend fun updateCollection(
        @PathVariable id: Long,
        @RequestBody request: UpdateCollectionRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): CollectionDto =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            updateCollectionUseCase
                .updateCollection(Collection(id = id, name = request.name, note = request.note), userId)
                .toEmptyDto()
        }

    @DeleteMapping("/{id}")
    suspend fun deleteCollection(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Unit> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            deleteCollectionUseCase.deleteCollection(id, userId)
            ResponseEntity.noContent().build()
        }

    @PostMapping("/{id}/transactions")
    suspend fun addTransaction(
        @PathVariable id: Long,
        @RequestBody request: CollectionTransactionRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Unit> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            manageCollectionMembersUseCase.addTransaction(id, request.transactionId, userId)
            ResponseEntity.noContent().build()
        }

    @DeleteMapping("/{id}/transactions/{transactionId}")
    suspend fun removeTransaction(
        @PathVariable id: Long,
        @PathVariable transactionId: Long,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Unit> =
        withContext(Dispatchers.IO) {
            val userId = resolveUserUseCase.resolveUser(principal.username)
            manageCollectionMembersUseCase.removeTransaction(id, transactionId, userId)
            ResponseEntity.noContent().build()
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
