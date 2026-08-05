package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.BulkCategoryUpdate
import com.moneylytics.api.application.port.input.BulkUpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.BurnRatePoint
import com.moneylytics.api.application.port.input.BurnRateResponse
import com.moneylytics.api.application.port.input.CalendarDaySum
import com.moneylytics.api.application.port.input.CalendarSumsQuery
import com.moneylytics.api.application.port.input.CalendarSumsResponse
import com.moneylytics.api.application.port.input.CashflowBucket
import com.moneylytics.api.application.port.input.CashflowResponse
import com.moneylytics.api.application.port.input.CategoryTotal
import com.moneylytics.api.application.port.input.CategoryTotalsResponse
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.GetBurnRateQuery
import com.moneylytics.api.application.port.input.GetBurnRateUseCase
import com.moneylytics.api.application.port.input.GetCalendarSumsUseCase
import com.moneylytics.api.application.port.input.GetCashflowQuery
import com.moneylytics.api.application.port.input.GetCashflowUseCase
import com.moneylytics.api.application.port.input.GetCategoryTotalsQuery
import com.moneylytics.api.application.port.input.GetCategoryTotalsUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.TransactionType
import com.moneylytics.api.application.port.input.UpdateTransactionAccountingDateUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.UpdateTransactionCommentUseCase
import com.moneylytics.api.application.port.input.bucketKey
import com.moneylytics.api.application.port.input.generateBuckets
import com.moneylytics.api.application.port.output.BudgetRepository
import com.moneylytics.api.application.port.output.CategoryClassifier
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.CategoryUpdateEntry
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
import com.moneylytics.api.domain.CategoryClassifierFeatures
import com.moneylytics.api.domain.Transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TransactionQueryService(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryClassifier: CategoryClassifier,
) : GetTransactionsUseCase,
    GetCashflowUseCase,
    GetBurnRateUseCase,
    GetCategoryTotalsUseCase,
    GetCalendarSumsUseCase,
    UpdateTransactionCategoryUseCase,
    UpdateTransactionCommentUseCase,
    UpdateTransactionAccountingDateUseCase,
    EnrichTransactionUseCase,
    BulkUpdateTransactionCategoryUseCase {
    override fun getTransactions(query: GetTransactionsQuery): List<Transaction> {
        val transactions = transactionRepository.findByAccountingDateBetween(query.from, query.to, query.organizationId, query.accountIban)
        return transactions
            .let { list ->
                when (query.type) {
                    TransactionType.INCOME -> list.filter { it.amount > BigDecimal.ZERO }
                    TransactionType.EXPENSES -> list.filter { it.amount < BigDecimal.ZERO }
                    TransactionType.ALL -> list
                }
            }.let { list -> if (query.uncategorized) list.filter { it.category == null } else list }
            .let { list -> query.category?.let { cat -> list.filter { it.category == cat } } ?: list }
            .let { list -> query.subcategory?.let { sub -> list.filter { it.subcategory == sub } } ?: list }
            .let { list -> query.group?.let { grp -> list.filter { it.group == grp } } ?: list }
            .let { list ->
                query.categoryId?.let { rootId ->
                    val all = categoryRepository.findAll(query.organizationId)
                    val subtree = collectSubtreeIds(rootId, all)
                    list.filter { it.categoryId in subtree }
                } ?: list
            }.let { list ->
                query.excludeCollectionId?.let { collectionId ->
                    val assigned = transactionRepository.findAssignedTransactionIdsByCollectionId(collectionId)
                    list.filter { it.id !in assigned }
                } ?: list
            }.let { list ->
                query.excludeBudgetId?.let { budgetId ->
                    val assigned = budgetRepository.findAssignedTransactionIdsByBudgetId(budgetId, query.organizationId)
                    list.filter { it.id !in assigned }
                } ?: list
            }
    }

    override fun getCashflow(query: GetCashflowQuery): CashflowResponse {
        val transactions = transactionRepository.findByAccountingDateBetween(query.from, query.to, query.organizationId, query.accountIban)
        val bucketKeys = generateBuckets(query.from, query.to, query.granularity)
        val byBucket = transactions.groupBy { bucketKey(it.accountingDate, query.granularity) }
        val buckets =
            bucketKeys.map { key ->
                val txns = byBucket[key] ?: emptyList()
                val incomeGross = txns.filter { it.amount >= BigDecimal.ZERO }.sumOf { it.amount }
                val incomeNet = txns.filter { it.effectiveAmount() >= BigDecimal.ZERO }.sumOf { it.effectiveAmount() }
                val expensesGross = txns.filter { it.amount < BigDecimal.ZERO }.sumOf { it.amount.abs() }
                val expensesNet = txns.filter { it.effectiveAmount() < BigDecimal.ZERO }.sumOf { it.effectiveAmount().abs() }
                CashflowBucket(
                    key = key,
                    incomeGross = incomeGross,
                    incomeNet = incomeNet,
                    expensesGross = expensesGross,
                    expensesNet = expensesNet,
                    net = incomeNet - expensesNet,
                )
            }
        return CashflowResponse(granularity = query.granularity, buckets = buckets)
    }

    override fun getBurnRate(query: GetBurnRateQuery): BurnRateResponse {
        val transactions = transactionRepository.findByAccountingDateBetween(query.from, query.to, query.organizationId, query.accountIban)
        val expensesByDate =
            transactions
                .filter { it.effectiveAmount() < BigDecimal.ZERO }
                .groupBy { it.accountingDate }
                .mapValues { (_, txns) -> txns.sumOf { it.effectiveAmount().abs() } }
        val incomeByDate =
            transactions
                .filter { it.effectiveAmount() > BigDecimal.ZERO }
                .groupBy { it.accountingDate }
                .mapValues { (_, txns) -> txns.sumOf { it.effectiveAmount() } }

        val dates =
            generateSequence(query.from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(query.to) }
                .toList()
        val dailyExpenses = dates.map { date -> expensesByDate[date] ?: BigDecimal.ZERO }

        var cumulative = BigDecimal.ZERO
        var cumulativeIncome = BigDecimal.ZERO
        val points =
            dates.mapIndexed { i, date ->
                cumulative += dailyExpenses[i]
                cumulativeIncome += incomeByDate[date] ?: BigDecimal.ZERO
                val windowStart = maxOf(0, i - query.rollingWindow + 1)
                val windowSum = dailyExpenses.subList(windowStart, i + 1).fold(BigDecimal.ZERO, BigDecimal::add)
                val windowSize = i - windowStart + 1
                val rollingAvg = windowSum.divide(BigDecimal(windowSize), 2, RoundingMode.HALF_UP)
                BurnRatePoint(
                    date = date.toString(),
                    expenses = dailyExpenses[i],
                    rollingAvg = rollingAvg,
                    cumulative = cumulative,
                    cumulativeIncome = cumulativeIncome,
                )
            }

        val totalExpenses = cumulative
        val totalIncome = cumulativeIncome
        val numberOfDays = ChronoUnit.DAYS.between(query.from, query.to) + 1
        val avgPerDay =
            if (totalExpenses > BigDecimal.ZERO) {
                totalExpenses.divide(BigDecimal(numberOfDays), 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        return BurnRateResponse(
            points = points,
            totalExpenses = totalExpenses,
            totalIncome = totalIncome,
            avgPerDay = avgPerDay,
        )
    }

    override fun getCategoryTotals(query: GetCategoryTotalsQuery): CategoryTotalsResponse {
        if (query.categoryId != null) {
            val allCategories = categoryRepository.findAll(query.organizationId)
            val transactions =
                getTransactions(
                    GetTransactionsQuery(
                        from = query.from,
                        to = query.to,
                        organizationId = query.organizationId,
                        type = TransactionType.EXPENSES,
                        accountIban = query.accountIban,
                        categoryId = query.categoryId,
                    ),
                )
            val directChildren = allCategories.filter { it.parentId == query.categoryId }
            val items =
                directChildren.mapNotNull { child ->
                    val childId = child.id ?: return@mapNotNull null
                    val childSubtreeIds = collectSubtreeIds(childId, allCategories)
                    val childTxns = transactions.filter { it.categoryId in childSubtreeIds }
                    if (childTxns.isEmpty()) return@mapNotNull null
                    CategoryTotal(
                        name = child.name,
                        value = childTxns.sumOf { it.effectiveAmount().abs() },
                        categoryId = childId,
                    )
                }
            return CategoryTotalsResponse(items = items.sortedByDescending { it.value })
        }

        val allCategories = categoryRepository.findAll(query.organizationId)
        val rootCatIds = allCategories.filter { it.parentId == null }.associate { it.name to it.id }
        val transactions =
            getTransactions(
                GetTransactionsQuery(
                    from = query.from,
                    to = query.to,
                    organizationId = query.organizationId,
                    type = TransactionType.EXPENSES,
                    accountIban = query.accountIban,
                    category = query.category,
                ),
            )
        val items =
            if (query.category == null) {
                transactions
                    .groupBy { it.category ?: "" }
                    .map { (name, txns) ->
                        CategoryTotal(
                            name = name,
                            value = txns.sumOf { it.effectiveAmount().abs() },
                            categoryId = rootCatIds[name],
                        )
                    }
            } else {
                transactions
                    .groupBy { it.group ?: "" }
                    .map { (name, txns) -> CategoryTotal(name = name, value = txns.sumOf { it.effectiveAmount().abs() }) }
            }
        return CategoryTotalsResponse(items = items.sortedByDescending { it.value })
    }

    override fun updateCategory(
        id: Long,
        organizationId: Long,
        categoryId: Long?,
    ): Transaction? {
        val updated = transactionRepository.updateCategory(id, organizationId, categoryId)
        if (categoryId != null &&
            updated != null &&
            (updated.purpose != null || updated.counterpartyName != null || updated.counterpartyIban != null)
        ) {
            categoryClassifier.train(
                organizationId,
                categoryId,
                CategoryClassifierFeatures(
                    purpose = updated.purpose,
                    counterpartyName = updated.counterpartyName,
                    counterpartyIban = updated.counterpartyIban,
                ),
            )
        }
        return updated
    }

    override fun updateComment(
        id: Long,
        organizationId: Long,
        comment: String?,
    ): Transaction? = transactionRepository.updateComment(id, organizationId, comment)

    override fun updateAccountingDate(
        id: Long,
        organizationId: Long,
        accountingDate: LocalDate,
    ): Transaction? = transactionRepository.updateAccountingDate(id, organizationId, accountingDate)

    override fun enrichByFingerprint(
        fingerprint: String,
        organizationId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
    ) = transactionRepository.enrichByFingerprint(fingerprint, organizationId, purpose, counterpartyName, counterpartyIban)

    override fun bulkUpdateCategory(
        updates: List<BulkCategoryUpdate>,
        organizationId: Long,
    ): List<Transaction> =
        transactionRepository.bulkUpdateCategory(
            updates.map { CategoryUpdateEntry(it.id, it.categoryId) },
            organizationId,
        )

    override fun getCalendarSums(query: CalendarSumsQuery): CalendarSumsResponse {
        val byDay = transactionRepository.sumExpensesByDay(query.from, query.to, query.organizationId, query.accountIban)
        return CalendarSumsResponse(
            data = byDay.map { (date, amount) -> CalendarDaySum(day = date.toString(), value = amount) },
        )
    }

    private fun collectSubtreeIds(
        rootId: Long,
        all: List<Category>,
    ): Set<Long> {
        val childrenByParent = all.groupBy { it.parentId }
        val result = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            result.add(id)
            childrenByParent[id]?.forEach { queue.add(requireNotNull(it.id)) }
        }
        return result
    }
}
