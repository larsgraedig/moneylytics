package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.MergeCategoriesCommand
import com.moneylytics.api.application.port.input.RevertMergeCommand
import com.moneylytics.api.application.port.output.CategoryMergeRepository
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
import com.moneylytics.api.domain.CategoryMerge
import com.moneylytics.api.domain.NewCategoryMerge
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class CategoryMergeServiceTest {
    private val categoryRepository: CategoryRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val thresholdRepository: ThresholdRepository = mock()
    private val categoryMergeRepository: CategoryMergeRepository = mock()
    private val service = CategoryMergeService(categoryRepository, transactionRepository, thresholdRepository, categoryMergeRepository)

    private val organizationId = 1L

    private fun merge(
        id: Long = 1L,
        sourceCategoryId: Long? = null,
    ) = CategoryMerge(
        id = id,
        sourceCategoryId = sourceCategoryId,
        sourceName = "Essen",
        sourceParentId = null,
        targetCategoryId = 2L,
        mergedAt = Instant.now(),
        transactionCount = 3,
        childCount = 0,
        reverted = false,
    )

    @Test
    fun `should move transactions and children, then delete source when no thresholds`() {
        whenever(categoryRepository.findAll(organizationId)).thenReturn(
            listOf(
                Category(id = 1L, name = "Essen", parentId = null),
                Category(id = 2L, name = "Lebensmittel", parentId = null),
            ),
        )
        whenever(transactionRepository.findIdsByCategoryId(1L, organizationId)).thenReturn(listOf(10L, 11L))
        whenever(categoryRepository.findChildIds(1L, organizationId)).thenReturn(listOf(5L))
        whenever(thresholdRepository.existsByCategoryId(1L, organizationId)).thenReturn(false)
        whenever(categoryMergeRepository.save(any())).thenReturn(merge())

        service.mergeCategories(MergeCategoriesCommand(sourceId = 1L, targetId = 2L, organizationId = organizationId))

        verify(transactionRepository).moveToCategoryBySource(1L, 2L, organizationId)
        verify(categoryRepository).reparentChildren(1L, 2L, organizationId)
        verify(categoryRepository).delete(1L, organizationId)
    }

    @Test
    fun `should keep source when thresholds exist`() {
        whenever(categoryRepository.findAll(organizationId)).thenReturn(
            listOf(
                Category(id = 1L, name = "Essen", parentId = null),
                Category(id = 2L, name = "Lebensmittel", parentId = null),
            ),
        )
        whenever(transactionRepository.findIdsByCategoryId(1L, organizationId)).thenReturn(emptyList())
        whenever(categoryRepository.findChildIds(1L, organizationId)).thenReturn(emptyList())
        whenever(thresholdRepository.existsByCategoryId(1L, organizationId)).thenReturn(true)
        whenever(categoryMergeRepository.save(any())).thenReturn(merge(sourceCategoryId = 1L))

        service.mergeCategories(MergeCategoriesCommand(sourceId = 1L, targetId = 2L, organizationId = organizationId))

        verify(categoryRepository, never()).delete(any(), any())
        val captor = org.mockito.kotlin.argumentCaptor<NewCategoryMerge>()
        verify(categoryMergeRepository).save(captor.capture())
        org.assertj.core.api.Assertions
            .assertThat(captor.firstValue.sourceCategoryId)
            .isEqualTo(1L)
    }

    @Test
    fun `should throw when source and target are the same`() {
        assertThatThrownBy {
            service.mergeCategories(MergeCategoriesCommand(sourceId = 1L, targetId = 1L, organizationId = organizationId))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("SAME_CATEGORY")
        verify(categoryRepository, never()).findAll(any())
    }

    @Test
    fun `should restore transactions and children on revert when source was deleted`() {
        val existingMerge = merge(id = 99L, sourceCategoryId = null)
        whenever(categoryMergeRepository.findById(99L, organizationId)).thenReturn(existingMerge)
        whenever(categoryMergeRepository.getTransactionIds(99L)).thenReturn(listOf(10L, 11L))
        whenever(categoryMergeRepository.getChildCategoryIds(99L)).thenReturn(listOf(5L))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(emptyList())
        whenever(categoryRepository.findOrCreate(listOf("Essen"), organizationId))
            .thenReturn(Category(id = 7L, name = "Essen", parentId = null))

        service.revertMerge(RevertMergeCommand(mergeId = 99L, organizationId = organizationId))

        verify(transactionRepository).moveToCategoryBulk(listOf(10L, 11L), 7L, organizationId)
        verify(categoryRepository).move(5L, 7L, organizationId)
        verify(categoryMergeRepository).markReverted(99L)
    }

    @Test
    fun `should restore transactions directly when source still exists`() {
        val existingMerge = merge(id = 99L, sourceCategoryId = 1L)
        whenever(categoryMergeRepository.findById(99L, organizationId)).thenReturn(existingMerge)
        whenever(categoryMergeRepository.getTransactionIds(99L)).thenReturn(listOf(10L))
        whenever(categoryMergeRepository.getChildCategoryIds(99L)).thenReturn(emptyList())

        service.revertMerge(RevertMergeCommand(mergeId = 99L, organizationId = organizationId))

        verify(transactionRepository).moveToCategoryBulk(listOf(10L), 1L, organizationId)
        verify(categoryRepository, never()).findOrCreate(any(), any())
        verify(categoryMergeRepository).markReverted(99L)
    }

    @Test
    fun `should throw ALREADY_REVERTED when reverting again`() {
        val alreadyReverted = merge(id = 5L).copy(reverted = true)
        whenever(categoryMergeRepository.findById(5L, organizationId)).thenReturn(alreadyReverted)

        assertThatThrownBy {
            service.revertMerge(RevertMergeCommand(mergeId = 5L, organizationId = organizationId))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("ALREADY_REVERTED")
    }

    @Test
    fun `should throw PARENT_NOT_FOUND when original parent no longer exists`() {
        val mergeWithParent = merge(id = 7L, sourceCategoryId = null).copy(sourceParentId = 99L)
        whenever(categoryMergeRepository.findById(7L, organizationId)).thenReturn(mergeWithParent)
        whenever(categoryMergeRepository.getTransactionIds(7L)).thenReturn(emptyList())
        whenever(categoryMergeRepository.getChildCategoryIds(7L)).thenReturn(emptyList())
        whenever(categoryRepository.findAll(organizationId)).thenReturn(emptyList())

        assertThatThrownBy {
            service.revertMerge(RevertMergeCommand(mergeId = 7L, organizationId = organizationId))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("PARENT_NOT_FOUND")
    }
}
