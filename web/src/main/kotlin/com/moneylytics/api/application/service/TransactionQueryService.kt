package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.BulkCategoryUpdate
import com.moneylytics.api.application.port.input.BulkUpdateTransactionCategoryUseCase
import com.moneylytics.api.application.port.input.BurnRatePoint
import com.moneylytics.api.application.port.input.BurnRateResponse
import com.moneylytics.api.application.port.input.CashflowBucket
import com.moneylytics.api.application.port.input.CashflowResponse
import com.moneylytics.api.application.port.input.CategoryTotal
import com.moneylytics.api.application.port.input.CategoryTotalsResponse
import com.moneylytics.api.application.port.input.EnrichTransactionUseCase
import com.moneylytics.api.application.port.input.GetBurnRateQuery
import com.moneylytics.api.application.port.input.GetBurnRateUseCase
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
import com.moneylytics.api.application.port.output.CategoryUpdateEntry
import com.moneylytics.api.application.port.output.TransactionRepository
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
) : GetTransactionsUseCase,
    GetCashflowUseCase,
    GetBurnRateUseCase,
    GetCategoryTotalsUseCase,
    UpdateTransactionCategoryUseCase,
    UpdateTransactionCommentUseCase,
    UpdateTransactionAccountingDateUseCase,
    EnrichTransactionUseCase,
    BulkUpdateTransactionCategoryUseCase {
    override fun getTransactions(query: GetTransactionsQuery): List<Transaction> {
        val transactions = transactionRepository.findByAccountingDateBetween(query.from, query.to, query.userId, query.accountIban)
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
            .let { list -> query.categoryGroup?.let { grp -> list.filter { it.categoryGroup == grp } } ?: list }
            .let { list ->
                query.excludeCollectionId?.let { collectionId ->
                    val assigned = transactionRepository.findAssignedTransactionIdsByCollectionId(collectionId)
                    list.filter { it.id !in assigned }
                } ?: list
            }.let { list ->
                query.excludeBudgetId?.let { budgetId ->
                    val assigned = budgetRepository.findAssignedTransactionIdsByBudgetId(budgetId, query.userId)
                    list.filter { it.id !in assigned }
                } ?: list
            }
    }

    override fun getCashflow(query: GetCashflowQuery): CashflowResponse {
        val transactions = transactionRepository.findByAccountingDateBetween(query.from, query.to, query.userId, query.accountIban)
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
        val transactions = transactionRepository.findByAccountingDateBetween(query.from, query.to, query.userId, query.accountIban)
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
        val transactions =
            getTransactions(
                GetTransactionsQuery(
                    from = query.from,
                    to = query.to,
                    userId = query.userId,
                    type = TransactionType.EXPENSES,
                    accountIban = query.accountIban,
                    category = query.category,
                ),
            )
        val items =
            if (query.category == null) {
                transactions
                    .groupBy { it.category ?: "" }
                    .map { (name, txns) -> CategoryTotal(name = name, value = txns.sumOf { it.effectiveAmount().abs() }) }
            } else {
                transactions
                    .groupBy { it.subcategory ?: "" }
                    .map { (name, txns) -> CategoryTotal(name = name, value = txns.sumOf { it.effectiveAmount().abs() }) }
            }
        return CategoryTotalsResponse(items = items.sortedByDescending { it.value })
    }

    override fun updateCategory(
        id: Long,
        userId: Long,
        category: String,
        subcategory: String,
        categoryGroup: String?,
    ): Transaction? = transactionRepository.updateCategory(id, userId, category, subcategory, categoryGroup)

    override fun updateComment(
        id: Long,
        userId: Long,
        comment: String?,
    ): Transaction? = transactionRepository.updateComment(id, userId, comment)

    override fun updateAccountingDate(
        id: Long,
        userId: Long,
        accountingDate: LocalDate,
    ): Transaction? = transactionRepository.updateAccountingDate(id, userId, accountingDate)

    override fun enrichByFingerprint(
        fingerprint: String,
        userId: Long,
        purpose: String?,
        counterpartyName: String?,
        counterpartyIban: String?,
    ) = transactionRepository.enrichByFingerprint(fingerprint, userId, purpose, counterpartyName, counterpartyIban, null)

    override fun bulkUpdateCategory(
        updates: List<BulkCategoryUpdate>,
        userId: Long,
    ): List<Transaction> =
        transactionRepository.bulkUpdateCategory(
            updates.map { CategoryUpdateEntry(it.id, it.category, it.subcategory, it.categoryGroup) },
            userId,
        )
}
