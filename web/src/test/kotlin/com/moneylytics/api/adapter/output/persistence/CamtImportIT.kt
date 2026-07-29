package com.moneylytics.api.adapter.output.persistence

import com.moneylytics.api.application.service.CategoryService
import com.moneylytics.api.application.service.IgnoredTransactionService
import com.moneylytics.api.domain.Category
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CamtImportIT : AbstractServiceIT() {
    @Autowired private lateinit var ignoredTransactionService: IgnoredTransactionService

    @Autowired private lateinit var categoryService: CategoryService

    @Autowired private lateinit var ignoredRepo: IgnoredTransactionJpaRepository

    @Autowired private lateinit var categoryRepo: CategoryJpaRepository

    // ── Ignored transaction lifecycle ────────────────────────────────────────

    @Test
    fun `should persist fingerprints in ignore list`() {
        ignoredTransactionService.update(
            toIgnore = listOf("fp1", "fp2"),
            toUnignore = emptyList(),
            organizationId = organizationId,
        )
        flushAndClear()

        val found = ignoredTransactionService.findIgnoredFingerprints(listOf("fp1", "fp2"), organizationId)

        assertThat(found).containsExactlyInAnyOrder("fp1", "fp2")
    }

    @Test
    fun `should remove fingerprints from ignore list on unignore`() {
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), organizationId = organizationId)
        flushAndClear()

        ignoredTransactionService.update(toIgnore = emptyList(), toUnignore = listOf("fp1"), organizationId = organizationId)
        flushAndClear()

        val found = ignoredTransactionService.findIgnoredFingerprints(listOf("fp1"), organizationId)

        assertThat(found).isEmpty()
    }

    @Test
    fun `should not create duplicate ignore entries when saved twice`() {
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), organizationId = organizationId)
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), organizationId = organizationId)
        flushAndClear()

        val existing = ignoredRepo.findExistingFingerprints(listOf("fp1"), organizationId)

        assertThat(existing).hasSize(1)
    }

    @Test
    fun `should isolate ignored fingerprints per user`() {
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), organizationId = organizationId)
        flushAndClear()

        val foundForOtherUser = ignoredTransactionService.findIgnoredFingerprints(listOf("fp1"), otherOrganizationId)

        assertThat(foundForOtherUser).isEmpty()
    }

    // ── Category deduplication ───────────────────────────────────────────────

    @Test
    fun `should save categories only once even when saved multiple times`() {
        val categories =
            listOf(
                Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt"),
                Category(name = "Transport", subcategory = null, group = "ÖPNV"),
            )
        categoryService.saveCategories(categories, organizationId)
        categoryService.saveCategories(categories, organizationId)
        flushAndClear()

        assertThat(categoryService.getCategories(organizationId)).hasSize(2)
    }

    @Test
    fun `should treat category as distinct when subcategory differs`() {
        categoryService.saveCategories(listOf(Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt")), organizationId)
        categoryService.saveCategories(
            listOf(Category(name = "Lebensmittel", subcategory = "Konsum", group = "Supermarkt")),
            organizationId,
        )
        flushAndClear()

        assertThat(categoryService.getCategories(organizationId)).hasSize(2)
    }

    @Test
    fun `should isolate categories per organization`() {
        categoryService.saveCategories(listOf(Category(name = "Lebensmittel", subcategory = null, group = "Supermarkt")), organizationId)
        flushAndClear()

        assertThat(categoryService.getCategories(otherOrganizationId)).isEmpty()
    }
}
