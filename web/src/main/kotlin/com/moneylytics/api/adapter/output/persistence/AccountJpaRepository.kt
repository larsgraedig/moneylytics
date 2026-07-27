package com.moneylytics.api.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface AccountJpaRepository : JpaRepository<AccountEntity, Long> {
    fun findByIbanAndOrganizationId(
        iban: String,
        organizationId: Long,
    ): AccountEntity?

    fun findAllByOrganizationId(organizationId: Long): List<AccountEntity>

    fun deleteByIbanAndOrganizationId(
        iban: String,
        organizationId: Long,
    )
}
