package com.moneylytics.api.application.port.input

interface ListUsersWithOrgsUseCase {
    fun listUsersWithOrgs(): UsersWithOrgs

    data class UsersWithOrgs(
        val organizations: List<OrgGroup>,
        val unorganized: List<String>,
    )

    data class OrgGroup(
        val id: Long,
        val name: String,
        val members: List<String>,
    )
}
