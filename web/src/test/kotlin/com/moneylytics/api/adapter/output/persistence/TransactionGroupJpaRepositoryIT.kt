package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TransactionGroupJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var groupRepo: TransactionGroupJpaRepository

    private fun savedGroup(
        name: String? = "Test Group",
        forOrganization: OrganizationEntity = organization,
    ) = groupRepo.save(TransactionGroupEntity(organization = forOrganization, name = name))

    @Test
    fun `should find all groups for user`() {
        savedGroup(name = "Gruppe A")
        savedGroup(name = "Gruppe B")
        savedGroup(name = "Fremde Gruppe", forOrganization = otherOrganization)

        val result = groupRepo.findAllByOrganizationId(organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Gruppe A", "Gruppe B")
    }

    @Test
    fun `should return empty list when user has no groups`() {
        val result = groupRepo.findAllByOrganizationId(otherOrganizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find group by id and user id`() {
        val group = savedGroup(name = "Meine Gruppe")
        val groupId = checkNotNull(group.id)

        val result = groupRepo.findByIdAndOrganizationId(groupId, organizationId)

        assertThat(result).isNotNull
        assertThat(result?.name).isEqualTo("Meine Gruppe")
    }

    @Test
    fun `should return null for group belonging to other user`() {
        val otherGroup = savedGroup(forOrganization = otherOrganization)
        val otherGroupId = checkNotNull(otherGroup.id)

        val result = groupRepo.findByIdAndOrganizationId(otherGroupId, organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should store and return group comment`() {
        val group = groupRepo.save(TransactionGroupEntity(organization = organization, comment = "Ein Kommentar"))
        val groupId = checkNotNull(group.id)

        val found = groupRepo.findByIdAndOrganizationId(groupId, organizationId)

        assertThat(found?.comment).isEqualTo("Ein Kommentar")
    }
}
