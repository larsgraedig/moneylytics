package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.port.output.OrganizationRepository
import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.Organization
import com.moneylytics.api.domain.OrganizationMembership
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrganizationPersistenceAdapter(
    private val organizationJpaRepository: OrganizationJpaRepository,
    private val memberJpaRepository: OrganizationMemberJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : OrganizationRepository {
    @Transactional
    override fun save(name: String): Organization = organizationJpaRepository.save(OrganizationEntity(name = name)).toDomain()

    override fun findById(id: Long): Organization? = organizationJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findByMemberUserId(userId: Long): List<OrganizationMembership> =
        memberJpaRepository.findByUserId(userId).map { it.toMembership() }

    @Transactional(readOnly = true)
    override fun findMembersByOrganizationId(organizationId: Long): List<OrganizationMembership> =
        memberJpaRepository.findByOrganizationId(organizationId).map { it.toMembership() }

    @Transactional
    override fun addMember(
        organizationId: Long,
        userId: Long,
        role: OrgRole,
    ) {
        val org = organizationJpaRepository.getReferenceById(organizationId)
        val user = userJpaRepository.getReferenceById(userId)
        memberJpaRepository.save(OrganizationMemberEntity(organization = org, user = user, role = role))
    }

    @Transactional
    override fun removeMember(
        organizationId: Long,
        userId: Long,
    ) = memberJpaRepository.deleteByOrganizationIdAndUserId(organizationId, userId)

    @Transactional
    override fun updateMemberRole(
        organizationId: Long,
        userId: Long,
        role: OrgRole,
    ) {
        memberJpaRepository.findByOrganizationIdAndUserId(organizationId, userId)?.also { it.role = role }
    }

    override fun isMember(
        organizationId: Long,
        userId: Long,
    ): Boolean = memberJpaRepository.existsByOrganizationIdAndUserId(organizationId, userId)

    override fun getMemberRole(
        organizationId: Long,
        userId: Long,
    ): OrgRole? = memberJpaRepository.findByOrganizationIdAndUserId(organizationId, userId)?.role

    override fun findAllOrganizationIds(): Set<Long> = organizationJpaRepository.findAll().mapNotNull { it.id }.toHashSet()

    override fun findAll(): List<Organization> = organizationJpaRepository.findAll().map { it.toDomain() }

    private fun OrganizationEntity.toDomain() = Organization(id = id!!, name = name)

    private fun OrganizationMemberEntity.toMembership() =
        OrganizationMembership(
            organization = organization.toDomain(),
            userId = user.id!!,
            email = user.externalId,
            role = role,
        )
}
