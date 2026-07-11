package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.AssignTransactionToBudgetUseCase
import com.moneylytics.api.application.port.input.CreateBudgetUseCase
import com.moneylytics.api.application.port.input.CreateCollectionUseCase
import com.moneylytics.api.application.port.input.CreateUserUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.ManageCollectionMembersUseCase
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.domain.Budget
import com.moneylytics.api.domain.Collection
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import com.moneylytics.api.domain.Transaction
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Random

@Profile("local")
@Component
class LocalDataInitializer(
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val createUserUseCase: CreateUserUseCase,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val manageTransactionOffsetUseCase: ManageTransactionOffsetUseCase,
    private val saveThresholdUseCase: SaveThresholdUseCase,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val assignTransactionToBudgetUseCase: AssignTransactionToBudgetUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val manageCollectionMembersUseCase: ManageCollectionMembersUseCase,
) : ApplicationRunner {
    private val mainIban = "DE00LOCAL000000000000"
    private val mainName = "Girokonto"

    private val savingsIban = "DE00LOCAL111111111111"
    private val savingsName = "Sparkonto"

    override fun run(args: ApplicationArguments) {
        val userId = createUserUseCase.createUser("local-dev-user", "local")
        importTransactionsUseCase.importTransactions(
            ImportTransactionsCommand(
                transactions = generateMainTransactions() + generateSavingsTransactions() + generateFixedTransactions(),
                accountNames =
                    mapOf(
                        mainIban to mainName,
                        savingsIban to savingsName,
                    ),
                userId = userId,
            ),
        )
        setupOffsetLinks(userId)
        setupThresholds(userId)
        setupBudgets(userId)
        setupCollections(userId)
    }

    private fun setupOffsetLinks(userId: Long) {
        val arztTx = findTx(userId, LocalDate.of(2025, 3, 15), "Gesundheit", BigDecimal("-180.00"))
        val erstattungTx = findTx(userId, LocalDate.of(2025, 3, 18), "Einnahmen", BigDecimal("120.00"))
        if (arztTx != null && erstattungTx != null) {
            manageTransactionOffsetUseCase.linkTransactions(
                LinkTransactionsCommand(
                    transactionId = arztTx.id!!,
                    otherTransactionId = erstattungTx.id!!,
                    myAmount = BigDecimal("120"),
                    otherAmount = BigDecimal("120"),
                    userId = userId,
                ),
            )
        }

        val restaurantTx = findTx(userId, LocalDate.of(2025, 6, 20), "Lebensmittel", BigDecimal("-95.00"))
        val uberweisungTx = findTx(userId, LocalDate.of(2025, 6, 21), "Einnahmen", BigDecimal("47.50"))
        if (restaurantTx != null && uberweisungTx != null) {
            manageTransactionOffsetUseCase.linkTransactions(
                LinkTransactionsCommand(
                    transactionId = restaurantTx.id!!,
                    otherTransactionId = uberweisungTx.id!!,
                    myAmount = BigDecimal("47.50"),
                    otherAmount = BigDecimal("47.50"),
                    userId = userId,
                ),
            )
        }
    }

    private fun setupThresholds(userId: Long) {
        saveThresholdUseCase.saveThreshold(
            Threshold(
                id = 0,
                category = "Lebensmittel",
                subcategory = null,
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("400"),
                warning = BigDecimal("600"),
                critical = BigDecimal("800"),
            ),
            userId,
        )
        saveThresholdUseCase.saveThreshold(
            Threshold(
                id = 0,
                category = "Transport",
                subcategory = null,
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("150"),
                warning = BigDecimal("250"),
                critical = null,
            ),
            userId,
        )
        saveThresholdUseCase.saveThreshold(
            Threshold(
                id = 0,
                category = "Freizeit",
                subcategory = null,
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("80"),
                warning = null,
                critical = null,
            ),
            userId,
        )
    }

    private fun setupBudgets(userId: Long) {
        val urlaub =
            createBudgetUseCase.createBudget(
                Budget(name = "Urlaub 2025", targetAmount = BigDecimal("1200")),
                userId,
            )
        listOf(
            LocalDate.of(2025, 4, 10) to BigDecimal("-380.00"),
            LocalDate.of(2025, 5, 22) to BigDecimal("-490.00"),
            LocalDate.of(2025, 7, 1) to BigDecimal("-200.00"),
        ).forEach { (date, amount) ->
            findTx(userId, date, "Reise", amount)?.id?.let { txId ->
                assignTransactionToBudgetUseCase.assignTransaction(urlaub.id!!, txId, null, userId)
            }
        }

        val kueche =
            createBudgetUseCase.createBudget(
                Budget(name = "Neue Küche", targetAmount = BigDecimal("3500")),
                userId,
            )
        findTx(userId, LocalDate.of(2025, 9, 5), "Wohnen", BigDecimal("-800.00"))?.id?.let { txId ->
            assignTransactionToBudgetUseCase.assignTransaction(kueche.id!!, txId, BigDecimal("500"), userId)
        }

        val notfall = createBudgetUseCase.createBudget(Budget(name = "Notfallfonds"), userId)
        listOf(
            Triple(LocalDate.of(2025, 1, 20), "Einnahmen", BigDecimal("500.00")),
            Triple(LocalDate.of(2025, 3, 5), "Einnahmen", BigDecimal("400.00")),
            Triple(LocalDate.of(2025, 4, 18), "Gesundheit", BigDecimal("-2300.00")),
            Triple(LocalDate.of(2025, 6, 10), "Einnahmen", BigDecimal("700.00")),
            Triple(LocalDate.of(2025, 8, 15), "Einnahmen", BigDecimal("900.00")),
        ).forEach { (date, category, amount) ->
            findTx(userId, date, category, amount)?.id?.let { txId ->
                assignTransactionToBudgetUseCase.assignTransaction(notfall.id!!, txId, null, userId)
            }
        }
    }

    private fun setupCollections(userId: Long) {
        val sommer = createCollectionUseCase.createCollection(Collection(name = "Sommer 2025"), userId)
        val sommerFrom = LocalDate.of(2025, 6, 1)
        val sommerTo = LocalDate.of(2025, 8, 31)
        val restaurantTxs = queryTxs(userId, sommerFrom, sommerTo, "Lebensmittel", "Restaurant").take(3)
        val sportTxs = queryTxs(userId, sommerFrom, sommerTo, "Freizeit", "Sport").take(2)
        (restaurantTxs + sportTxs).forEach { tx ->
            tx.id?.let { manageCollectionMembersUseCase.addTransaction(sommer.id!!, it, userId) }
        }

        val haushalt = createCollectionUseCase.createCollection(Collection(name = "Haushalt Q1 2025"), userId)
        val q1From = LocalDate.of(2025, 1, 1)
        val q1To = LocalDate.of(2025, 3, 31)
        val mieteTxs = queryTxs(userId, q1From, q1To, "Wohnen", "Miete").take(2)
        val internetTxs = queryTxs(userId, q1From, q1To, "Wohnen", "Internet").take(2)
        (mieteTxs + internetTxs).forEach { tx ->
            tx.id?.let { manageCollectionMembersUseCase.addTransaction(haushalt.id!!, it, userId) }
        }
    }

    private fun findTx(
        userId: Long,
        date: LocalDate,
        category: String,
        amount: BigDecimal,
    ): Transaction? =
        getTransactionsUseCase
            .getTransactions(GetTransactionsQuery(from = date, to = date, userId = userId, category = category))
            .find { it.amount.compareTo(amount) == 0 }

    private fun queryTxs(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
        category: String,
        subcategory: String,
    ): List<Transaction> =
        getTransactionsUseCase.getTransactions(
            GetTransactionsQuery(from = from, to = to, userId = userId, category = category, subcategory = subcategory),
        )

    private fun generateFixedTransactions(): List<Transaction> =
        listOf(
            tx("Gesundheit", "Arzt", LocalDate.of(2025, 3, 15), BigDecimal("-180.00")),
            tx("Einnahmen", "Erstattung", LocalDate.of(2025, 3, 18), BigDecimal("120.00")),
            tx("Lebensmittel", "Restaurant", LocalDate.of(2025, 6, 20), BigDecimal("-95.00")),
            tx("Einnahmen", "Überweisung", LocalDate.of(2025, 6, 21), BigDecimal("47.50")),
            tx("Reise", "Flug", LocalDate.of(2025, 4, 10), BigDecimal("-380.00")),
            tx("Reise", "Hotel", LocalDate.of(2025, 5, 22), BigDecimal("-490.00")),
            tx("Reise", "Aktivitäten", LocalDate.of(2025, 7, 1), BigDecimal("-200.00")),
            tx("Wohnen", "Einrichtung", LocalDate.of(2025, 9, 5), BigDecimal("-800.00")),
            tx("Einnahmen", "Sonstiges", LocalDate.of(2025, 1, 20), BigDecimal("500.00")),
            tx("Einnahmen", "Sonstiges", LocalDate.of(2025, 3, 5), BigDecimal("400.00")),
            tx("Gesundheit", "Behandlung", LocalDate.of(2025, 4, 18), BigDecimal("-2300.00")),
            tx("Einnahmen", "Sonstiges", LocalDate.of(2025, 6, 10), BigDecimal("700.00")),
            tx("Einnahmen", "Sonstiges", LocalDate.of(2025, 8, 15), BigDecimal("900.00")),
        )

    private fun tx(
        category: String,
        subcategory: String,
        date: LocalDate,
        amount: BigDecimal,
    ) = Transaction(
        category = category,
        subcategory = subcategory,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = mainIban,
    )

    private fun generateMainTransactions(): List<Transaction> {
        val rng = Random(42)
        val transactions = mutableListOf<Transaction>()

        val months =
            generateSequence(LocalDate.of(2024, 1, 1)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(LocalDate.of(2025, 12, 1)) }
                .toList()

        for (month in months) {
            val daysInMonth = month.lengthOfMonth()

            fun day(d: Int) = month.withDayOfMonth(d.coerceIn(1, daysInMonth))

            fun randomDay() = day(rng.nextInt(daysInMonth) + 1)

            fun euros(
                from: Double,
                to: Double,
            ) = BigDecimal
                .valueOf(from + rng.nextDouble() * (to - from))
                .setScale(2, RoundingMode.HALF_UP)

            fun tx(
                category: String,
                subcategory: String,
                date: LocalDate,
                amount: BigDecimal,
            ) = Transaction(
                category = category,
                subcategory = subcategory,
                bookingDate = date,
                valueDate = date,
                accountingDate = date,
                amount = amount,
                currency = "EUR",
                accountIban = mainIban,
            )

            transactions += tx("Einnahmen", "Gehalt", day(28), euros(2700.0, 3100.0))
            transactions += tx("Wohnen", "Miete", day(1), -euros(950.0, 950.0))
            transactions += tx("Wohnen", "Internet", day(15), BigDecimal("-39.99"))
            transactions += tx("Freizeit", "Streaming", day(10), BigDecimal("-17.99"))
            transactions += tx("Transport", "ÖPNV", day(3), BigDecimal("-86.00"))

            repeat(rng.nextInt(4) + 2) {
                transactions += tx("Lebensmittel", "Supermarkt", randomDay(), -euros(20.0, 120.0))
            }
            repeat(rng.nextInt(3) + 1) {
                transactions += tx("Lebensmittel", "Restaurant", randomDay(), -euros(14.0, 65.0))
            }

            if (rng.nextDouble() < 0.5) transactions += tx("Gesundheit", "Apotheke", randomDay(), -euros(7.0, 52.0))
            if (rng.nextDouble() < 0.4) transactions += tx("Transport", "Tankstelle", randomDay(), -euros(40.0, 78.0))
            if (rng.nextDouble() < 0.3) transactions += tx("Shopping", "Kleidung", randomDay(), -euros(29.0, 210.0))
            if (rng.nextDouble() < 0.25) transactions += tx("Freizeit", "Sport", randomDay(), -euros(18.0, 85.0))
            if (rng.nextDouble() < 0.15) transactions += tx("Gesundheit", "Arzt", randomDay(), -euros(25.0, 120.0))
            if (rng.nextDouble() < 0.1) transactions += tx("Shopping", "Elektronik", randomDay(), -euros(50.0, 500.0))
        }

        return transactions.sortedBy { it.bookingDate }
    }

    private fun generateSavingsTransactions(): List<Transaction> {
        val rng = Random(99)
        val transactions = mutableListOf<Transaction>()

        val months =
            generateSequence(LocalDate.of(2024, 1, 1)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(LocalDate.of(2025, 12, 1)) }
                .toList()

        for (month in months) {
            val daysInMonth = month.lengthOfMonth()

            fun day(d: Int) = month.withDayOfMonth(d.coerceIn(1, daysInMonth))

            fun randomDay() = day(rng.nextInt(daysInMonth) + 1)

            fun euros(
                from: Double,
                to: Double,
            ) = BigDecimal
                .valueOf(from + rng.nextDouble() * (to - from))
                .setScale(2, RoundingMode.HALF_UP)

            fun tx(
                category: String,
                subcategory: String,
                date: LocalDate,
                amount: BigDecimal,
            ) = Transaction(
                category = category,
                subcategory = subcategory,
                bookingDate = date,
                valueDate = date,
                accountingDate = date,
                amount = amount,
                currency = "EUR",
                accountIban = savingsIban,
            )

            transactions += tx("Einnahmen", "Zinsen", day(1), euros(2.0, 12.0))
            transactions += tx("Sparen", "Einzahlung", day(5), -euros(200.0, 500.0))

            if (rng.nextDouble() < 0.3) transactions += tx("Sparen", "Entnahme", randomDay(), euros(100.0, 300.0))
            if (rng.nextDouble() < 0.15) transactions += tx("Versicherung", "Jahresbeitrag", randomDay(), -euros(80.0, 250.0))
        }

        return transactions.sortedBy { it.bookingDate }
    }
}
