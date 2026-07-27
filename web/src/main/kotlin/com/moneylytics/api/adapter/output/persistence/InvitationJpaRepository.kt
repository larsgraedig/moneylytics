package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface InvitationJpaRepository : JpaRepository<InvitationEntity, Long> {
    fun findByToken(token: String): InvitationEntity?
}
