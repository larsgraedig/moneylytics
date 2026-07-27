package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.ActivateOrganizationUseCase
import com.moneylytics.api.application.port.input.AdminManageOrgMembersUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.GetOrganizationsUseCase
import com.moneylytics.api.application.port.input.ListUsersWithOrgsUseCase
import com.moneylytics.api.application.port.input.ManageOrganizationMembersUseCase
import com.moneylytics.api.application.port.input.OnboardOrganizationUseCase
import com.moneylytics.api.application.port.input.OrganizationLogoUseCase
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
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebSession

const val SESSION_KEY_ACTIVE_ORG = "activeOrganizationId"

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository,
) : ResolveOrganizationUseCase,
    GetOrganizationsUseCase,
    CreateOrganizationUseCase,
    ManageOrganizationMembersUseCase,
    ActivateOrganizationUseCase,
    RequireOrgRoleUseCase,
    ListUsersWithOrgsUseCase,
    OnboardOrganizationUseCase,
    AdminManageOrgMembersUseCase,
    OrganizationLogoUseCase {
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

    override fun onboardOrganization(
        name: String,
        userId: Long,
    ): Organization {
        val existing = organizationRepository.findByMemberUserId(userId)
        if (existing.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User already belongs to an organization")
        }
        return createOrganization(name, userId)
    }

    override fun getMembers(organizationId: Long): List<OrganizationMembership> =
        organizationRepository.findMembersByOrganizationId(organizationId)

    override fun removeMember(
        organizationId: Long,
        targetUserId: Long,
        requestingUserId: Long,
    ) {
        check(targetUserId != requestingUserId) { "Users cannot remove themselves from an organization" }
        requireOrgRole(organizationId, requestingUserId, OrgRole.ADMIN)
        organizationRepository.removeMember(organizationId, targetUserId)
    }

    override fun updateMemberRole(
        organizationId: Long,
        targetUserId: Long,
        role: OrgRole,
        requestingUserId: Long,
    ) {
        check(targetUserId != requestingUserId) { "Users cannot change their own role" }
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

    override fun listUsersWithOrgs(): ListUsersWithOrgsUseCase.UsersWithOrgs {
        val orgGroups =
            organizationRepository.findAll().map { org ->
                val members = organizationRepository.findMembersByOrganizationId(org.id).map { it.email }
                ListUsersWithOrgsUseCase.OrgGroup(id = org.id, name = org.name, members = members)
            }
        val organizedEmails = orgGroups.flatMap { it.members }.toSet()
        val unorganized = userRepository.findAll().map { it.externalId }.filter { it !in organizedEmails }
        return ListUsersWithOrgsUseCase.UsersWithOrgs(organizations = orgGroups, unorganized = unorganized)
    }

    override fun adminAddMember(
        orgId: Long,
        externalId: String,
        role: OrgRole,
    ) {
        val userId =
            userRepository.findByExternalId(externalId)?.id
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: $externalId")
        organizationRepository.addMember(orgId, userId, role)
    }

    override fun adminRemoveMember(
        orgId: Long,
        externalId: String,
    ) {
        val userId =
            userRepository.findByExternalId(externalId)?.id
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: $externalId")
        organizationRepository.removeMember(orgId, userId)
    }

    override fun uploadLogo(
        orgId: Long,
        logoData: ByteArray,
        contentType: String,
        requestingUserId: Long,
    ) {
        requireOrgRole(orgId, requestingUserId, OrgRole.ADMIN)
        organizationRepository.updateLogo(orgId, logoData, contentType)
    }

    override fun deleteLogo(
        orgId: Long,
        requestingUserId: Long,
    ) {
        requireOrgRole(orgId, requestingUserId, OrgRole.ADMIN)
        organizationRepository.updateLogo(orgId, null, null)
    }

    override fun getLogo(orgId: Long): Pair<ByteArray, String>? = organizationRepository.getLogo(orgId)

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
