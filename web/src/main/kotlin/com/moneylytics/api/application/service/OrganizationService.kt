package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ActivateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.GetOrganizationsUseCase
import com.moneylytics.api.application.port.input.ManageOrganizationMembersUseCase
import com.moneylytics.api.application.port.input.RegisterUserUseCase
import com.moneylytics.api.application.port.input.RequireOrgRoleUseCase
import com.moneylytics.api.application.port.input.ResolveOrganizationUseCase
import com.moneylytics.api.application.port.output.OrganizationRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.OrgRole
import com.moneylytics.api.domain.Organization
import com.moneylytics.api.domain.OrganizationMembership
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebSession

const val SESSION_KEY_ACTIVE_ORG = "activeOrganizationId"

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository,
    private val registerUserUseCase: RegisterUserUseCase,
) : ResolveOrganizationUseCase,
    GetOrganizationsUseCase,
    CreateOrganizationUseCase,
    ManageOrganizationMembersUseCase,
    ActivateOrganizationUseCase,
    RequireOrgRoleUseCase {
    override suspend fun resolveOrganization(
        principal: UserDetails,
        exchange: ServerWebExchange,
    ): Long {
        val session = exchange.session.awaitSingle()
        return withContext(Dispatchers.IO) {
            val userId =
                userRepository.findByExternalId(principal.username)?.id
                    ?: error("User not found: ${principal.username}")
            val orgId = session.attributes[SESSION_KEY_ACTIVE_ORG] as? Long
            if (orgId != null && organizationRepository.isMember(orgId, userId)) return@withContext orgId
            val firstOrg =
                organizationRepository
                    .findByMemberUserId(userId)
                    .firstOrNull()
                    ?.organization
                    ?.id
                    ?: error("User $userId has no organization")
            session.attributes[SESSION_KEY_ACTIVE_ORG] = firstOrg
            firstOrg
        }
    }

    override fun getOrganizations(userId: Long): List<OrganizationMembership> = organizationRepository.findByMemberUserId(userId)

    override fun createOrganization(
        name: String,
        ownerUserId: Long,
    ): Organization {
        val org = organizationRepository.save(name)
        organizationRepository.addMember(org.id, ownerUserId, OrgRole.OWNER)
        return org
    }

    override fun getMembers(organizationId: Long): List<OrganizationMembership> =
        organizationRepository.findMembersByOrganizationId(organizationId)

    override fun addMember(
        organizationId: Long,
        email: String,
        password: String,
        role: OrgRole,
        requestingUserId: Long,
    ) {
        requireOrgRole(organizationId, requestingUserId, OrgRole.ADMIN)
        val newUserId = registerUserUseCase.registerUser(email, password)
        organizationRepository.addMember(organizationId, newUserId, role)
    }

    override fun removeMember(
        organizationId: Long,
        targetUserId: Long,
        requestingUserId: Long,
    ) {
        requireOrgRole(organizationId, requestingUserId, OrgRole.ADMIN)
        organizationRepository.removeMember(organizationId, targetUserId)
    }

    override fun updateMemberRole(
        organizationId: Long,
        targetUserId: Long,
        role: OrgRole,
        requestingUserId: Long,
    ) {
        requireOrgRole(organizationId, requestingUserId, OrgRole.ADMIN)
        organizationRepository.updateMemberRole(organizationId, targetUserId, role)
    }

    override suspend fun activateOrganization(
        organizationId: Long,
        userId: Long,
        session: WebSession,
    ) {
        check(organizationRepository.isMember(organizationId, userId)) {
            "User $userId is not a member of organization $organizationId"
        }
        session.attributes[SESSION_KEY_ACTIVE_ORG] = organizationId
    }

    override fun requireOrgRole(
        organizationId: Long,
        userId: Long,
        minimumRole: OrgRole,
    ) {
        val role =
            organizationRepository.getMemberRole(organizationId, userId)
                ?: error("User $userId is not a member of organization $organizationId")
        check(role >= minimumRole) { "User $userId does not have sufficient role in organization $organizationId" }
    }
}
