package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TransactionGroupJpaRepositoryIT : AbstractJpaRepositoryIT() {
    @Autowired private lateinit var groupRepo: TransactionGroupJpaRepository

    private fun savedGroup(
        name: String? = "Test Group",
        forUser: UserEntity = user,
    ) = groupRepo.save(TransactionGroupEntity(user = forUser, name = name))

    @Test
    fun `should find all groups for user`() {
        savedGroup(name = "Gruppe A")
        savedGroup(name = "Gruppe B")
        savedGroup(name = "Fremde Gruppe", forUser = otherUser)

        val result = groupRepo.findAllByUserId(user.id!!)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("Gruppe A", "Gruppe B")
    }

    @Test
    fun `should return empty list when user has no groups`() {
        val result = groupRepo.findAllByUserId(otherUser.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find group by id and user id`() {
        val group = savedGroup(name = "Meine Gruppe")

        val result = groupRepo.findByIdAndUserId(group.id!!, user.id!!)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("Meine Gruppe")
    }

    @Test
    fun `should return null for group belonging to other user`() {
        val otherGroup = savedGroup(forUser = otherUser)

        val result = groupRepo.findByIdAndUserId(otherGroup.id!!, user.id!!)

        assertThat(result).isNull()
    }

    @Test
    fun `should store and return group comment`() {
        val group = groupRepo.save(TransactionGroupEntity(user = user, comment = "Ein Kommentar"))

        val found = groupRepo.findByIdAndUserId(group.id!!, user.id!!)

        assertThat(found!!.comment).isEqualTo("Ein Kommentar")
    }
}
