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
            userId = userId,
        )
        flushAndClear()

        val found = ignoredTransactionService.findIgnoredFingerprints(listOf("fp1", "fp2"), userId)

        assertThat(found).containsExactlyInAnyOrder("fp1", "fp2")
    }

    @Test
    fun `should remove fingerprints from ignore list on unignore`() {
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), userId = userId)
        flushAndClear()

        ignoredTransactionService.update(toIgnore = emptyList(), toUnignore = listOf("fp1"), userId = userId)
        flushAndClear()

        val found = ignoredTransactionService.findIgnoredFingerprints(listOf("fp1"), userId)

        assertThat(found).isEmpty()
    }

    @Test
    fun `should not create duplicate ignore entries when saved twice`() {
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), userId = userId)
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), userId = userId)
        flushAndClear()

        val existing = ignoredRepo.findExistingFingerprints(listOf("fp1"), userId)

        assertThat(existing).hasSize(1)
    }

    @Test
    fun `should isolate ignored fingerprints per user`() {
        ignoredTransactionService.update(toIgnore = listOf("fp1"), toUnignore = emptyList(), userId = userId)
        flushAndClear()

        val foundForOtherUser = ignoredTransactionService.findIgnoredFingerprints(listOf("fp1"), otherUserId)

        assertThat(foundForOtherUser).isEmpty()
    }

    // ── Category deduplication ───────────────────────────────────────────────

    @Test
    fun `should save categories only once even when saved multiple times`() {
        val categories =
            listOf(
                Category(name = "Lebensmittel", subcategory = "Supermarkt"),
                Category(name = "Transport", subcategory = "ÖPNV"),
            )
        categoryService.saveCategories(categories, userId)
        categoryService.saveCategories(categories, userId)
        flushAndClear()

        assertThat(categoryService.getCategories(userId)).hasSize(2)
    }

    @Test
    fun `should treat category as distinct when group differs`() {
        categoryService.saveCategories(listOf(Category("Lebensmittel", "Supermarkt", group = null)), userId)
        categoryService.saveCategories(listOf(Category("Lebensmittel", "Supermarkt", group = "Konsum")), userId)
        flushAndClear()

        assertThat(categoryService.getCategories(userId)).hasSize(2)
    }

    @Test
    fun `should isolate categories per user`() {
        categoryService.saveCategories(listOf(Category("Lebensmittel", "Supermarkt")), userId)
        flushAndClear()

        assertThat(categoryService.getCategories(otherUserId)).isEmpty()
    }
}
