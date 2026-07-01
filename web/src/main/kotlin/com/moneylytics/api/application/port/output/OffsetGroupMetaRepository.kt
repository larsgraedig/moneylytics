package com.moneylytics.api.application.port.output

interface OffsetGroupMetaRepository {
    fun findAllForUser(userId: Long): Map<Long, Pair<String?, String?>>

    fun upsert(
        userId: Long,
        groupKey: Long,
        name: String?,
        comment: String?,
    )
}
