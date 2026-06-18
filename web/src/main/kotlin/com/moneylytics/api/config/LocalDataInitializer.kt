package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.CreateUserUseCase
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
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
) : ApplicationRunner {
    private val mainIban = "DE00LOCAL000000000000"
    private val mainName = "Girokonto"

    private val savingsIban = "DE00LOCAL111111111111"
    private val savingsName = "Sparkonto"

    override fun run(args: ApplicationArguments) {
        val userId = createUserUseCase.createUser("local-dev-user", "local")
        importTransactionsUseCase.importTransactions(
            ImportTransactionsCommand(
                transactions = generateMainTransactions() + generateSavingsTransactions(),
                accountNames =
                    mapOf(
                        mainIban to mainName,
                        savingsIban to savingsName,
                    ),
                userId = userId,
            ),
        )
    }

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
