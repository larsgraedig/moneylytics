package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface AccountJpaRepository : JpaRepository<AccountEntity, Long> {
    fun findByIbanAndUserId(
        iban: String,
        userId: Long,
    ): AccountEntity?

    fun findAllByUserId(userId: Long): List<AccountEntity>
}
