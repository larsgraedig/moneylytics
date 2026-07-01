package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.OffsetGroupMetaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OffsetGroupMetaPersistenceAdapter(
    private val jpaRepository: OffsetGroupMetaJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : OffsetGroupMetaRepository {
    override fun findAllForUser(userId: Long): Map<Long, Pair<String?, String?>> =
        jpaRepository.findAllByUserId(userId).associate { it.groupKey to (it.name to it.comment) }

    @Transactional
    override fun upsert(
        userId: Long,
        groupKey: Long,
        name: String?,
        comment: String?,
    ) {
        val entity =
            jpaRepository.findByUserIdAndGroupKey(userId, groupKey)
                ?.also { it.name = name?.takeIf { s -> s.isNotBlank() }; it.comment = comment?.takeIf { s -> s.isNotBlank() } }
                ?: OffsetGroupMetaEntity(
                    user = userJpaRepository.getReferenceById(userId),
                    groupKey = groupKey,
                    name = name?.takeIf { it.isNotBlank() },
                    comment = comment?.takeIf { it.isNotBlank() },
                )
        jpaRepository.save(entity)
    }
}
