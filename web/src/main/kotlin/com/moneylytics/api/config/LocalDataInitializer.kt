package com.moneylytics.api.config

import com.moneylytics.api.application.port.input.AssignTierToUserUseCase
import com.moneylytics.api.application.port.input.AssignTransactionToBudgetUseCase
import com.moneylytics.api.application.port.input.CreateBudgetUseCase
import com.moneylytics.api.application.port.input.CreateCollectionUseCase
import com.moneylytics.api.application.port.input.CreateOrganizationUseCase
import com.moneylytics.api.application.port.input.CreateTierUseCase
import com.moneylytics.api.application.port.input.CreateUserUseCase
import com.moneylytics.api.application.port.input.DetectRecurringSeriesUseCase
import com.moneylytics.api.application.port.input.GetTransactionsQuery
import com.moneylytics.api.application.port.input.GetTransactionsUseCase
import com.moneylytics.api.application.port.input.ImportFileSpec
import com.moneylytics.api.application.port.input.ImportTransactionsCommand
import com.moneylytics.api.application.port.input.ImportTransactionsUseCase
import com.moneylytics.api.application.port.input.LinkTransactionsCommand
import com.moneylytics.api.application.port.input.ManageCollectionMembersUseCase
import com.moneylytics.api.application.port.input.ManageTransactionOffsetUseCase
import com.moneylytics.api.application.port.input.RefreshRecurringSeriesCommand
import com.moneylytics.api.application.port.input.SaveThresholdUseCase
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.InvoiceRepository
import com.moneylytics.api.application.port.output.StripeCustomerRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.application.port.output.UserRepository
import com.moneylytics.api.domain.Budget
import com.moneylytics.api.domain.Collection
import com.moneylytics.api.domain.ImportFileType
import com.moneylytics.api.domain.Invoice
import com.moneylytics.api.domain.StripeCustomer
import com.moneylytics.api.domain.SubscriptionStatus
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import com.moneylytics.api.domain.Transaction
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Random

private val logger = KotlinLogging.logger {}

@Suppress("MagicNumber")
@Profile("local")
@Component
class LocalDataInitializer(
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val createUserUseCase: CreateUserUseCase,
    private val createOrganizationUseCase: CreateOrganizationUseCase,
    private val userRepository: UserRepository,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val manageTransactionOffsetUseCase: ManageTransactionOffsetUseCase,
    private val saveThresholdUseCase: SaveThresholdUseCase,
    private val categoryRepository: CategoryRepository,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val assignTransactionToBudgetUseCase: AssignTransactionToBudgetUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val manageCollectionMembersUseCase: ManageCollectionMembersUseCase,
    private val detectRecurringSeriesUseCase: DetectRecurringSeriesUseCase,
    private val assignTierToUserUseCase: AssignTierToUserUseCase,
    private val createTierUseCase: CreateTierUseCase,
    private val stripeCustomerRepository: StripeCustomerRepository,
    private val invoiceRepository: InvoiceRepository,
    private val transactionRepository: TransactionRepository,
) : ApplicationRunner {
    companion object {
        private const val MAIN_RNG_SEED = 42L
        private const val SAVINGS_RNG_SEED = 99L

        private val ARZT_DATE = LocalDate.of(2025, 3, 15)
        private val ERSTATTUNG_DATE = LocalDate.of(2025, 3, 18)
        private val RESTAURANT_DATE = LocalDate.of(2025, 6, 20)
        private val UEBERWEISUNG_DATE = LocalDate.of(2025, 6, 21)
        private val REISE_FLUG_DATE = LocalDate.of(2025, 4, 10)
        private val REISE_HOTEL_DATE = LocalDate.of(2025, 5, 22)
        private val REISE_AKTIVITAETEN_DATE = LocalDate.of(2025, 7, 1)
        private val WOHNEN_EINRICHTUNG_DATE = LocalDate.of(2025, 9, 5)
        private val EINNAHMEN_JAN_20_DATE = LocalDate.of(2025, 1, 20)
        private val EINNAHMEN_MAR_5_DATE = LocalDate.of(2025, 3, 5)
        private val GESUNDHEIT_APR_DATE = LocalDate.of(2025, 4, 18)
        private val EINNAHMEN_JUN_10_DATE = LocalDate.of(2025, 6, 10)
        private val EINNAHMEN_AUG_DATE = LocalDate.of(2025, 8, 15)

        private val SUGGESTION_REWE_DATE = LocalDate.of(2025, 12, 3)
        private val SUGGESTION_TANKSTELLE_DATE = LocalDate.of(2025, 12, 8)
        private val SUGGESTION_NETFLIX_DATE = LocalDate.of(2025, 12, 11)
        private val SUGGESTION_ZALANDO_DATE = LocalDate.of(2025, 12, 15)
        private val SUGGESTION_ALLIANZ_DATE = LocalDate.of(2025, 12, 22)
        private val SUGGESTION_FROM = LocalDate.of(2025, 12, 1)
        private val SUGGESTION_TO = LocalDate.of(2025, 12, 31)

        private val SOMMER_FROM = LocalDate.of(2025, 6, 1)
        private val SOMMER_TO = LocalDate.of(2025, 8, 31)
        private val Q1_FROM = LocalDate.of(2025, 1, 1)
        private val Q1_TO = LocalDate.of(2025, 3, 31)

        private val GENERATED_START = LocalDate.of(2024, 1, 1)
        private val GENERATED_END = LocalDate.of(2025, 12, 1)

        private const val SALARY_DAY = 28
        private const val INTERNET_DAY = 15
        private const val STREAMING_DAY = 10
        private const val OPNV_DAY = 3
        private const val SPAREINZAHLUNG_DAY = 5
        private const val INSURANCE_DAY = 2
        private const val GYM_DAY = 1
        private const val MUSIC_DAY = 12

        private const val SALARY_MIN = 2700.0
        private const val SALARY_MAX = 3100.0
        private const val RENT_AMOUNT = 950.0

        private const val SUPERMARKT_MAX_REPEAT = 4
        private const val RESTAURANT_MAX_REPEAT = 3
        private const val SOMMER_RESTAURANT_LIMIT = 3

        private const val SUPERMARKT_MIN = 20.0
        private const val SUPERMARKT_MAX = 120.0
        private const val RESTAURANT_MIN = 14.0
        private const val RESTAURANT_MAX = 65.0
        private const val APOTHEKE_MIN = 7.0
        private const val APOTHEKE_MAX = 52.0
        private const val TANKSTELLE_MIN = 40.0
        private const val TANKSTELLE_MAX = 78.0
        private const val KLEIDUNG_MIN = 29.0
        private const val KLEIDUNG_MAX = 210.0
        private const val SPORT_MIN = 18.0
        private const val SPORT_MAX = 85.0
        private const val ARZT_MIN = 25.0
        private const val ARZT_MAX = 120.0
        private const val ELEKTRONIK_MIN = 50.0
        private const val ELEKTRONIK_MAX = 500.0

        private const val APOTHEKE_PROBABILITY = 0.5
        private const val TANKSTELLE_PROBABILITY = 0.4
        private const val KLEIDUNG_PROBABILITY = 0.3
        private const val SPORT_PROBABILITY = 0.25
        private const val ARZT_PROBABILITY = 0.15
        private const val ELEKTRONIK_PROBABILITY = 0.1

        private const val ZINSEN_MAX = 12.0
        private const val SPAREINZAHLUNG_MIN = 200.0
        private const val SPAREINZAHLUNG_MAX = 500.0
        private const val ENTNAHME_MIN = 100.0
        private const val ENTNAHME_MAX = 300.0
        private const val JAHRESBEITRAG_MIN = 80.0
        private const val JAHRESBEITRAG_MAX = 250.0

        private const val ENTNAHME_PROBABILITY = 0.3
        private const val JAHRESBEITRAG_PROBABILITY = 0.15
    }

    private val mainIban = "DE00LOCAL000000000000"
    private val mainName = "Girokonto"

    private val savingsIban = "DE00LOCAL111111111111"
    private val savingsName = "Sparkonto"

    override fun run(args: ApplicationArguments) {
        if (userRepository.findAll().isNotEmpty()) {
            logger.info { "Database already contains data — skipping local seed data." }
            return
        }

        createTierUseCase.createTier("Standard", "Standard Tier", isDefault = true)
        val proTier = createTierUseCase.createTier("Pro", "Pro Tier", isDefault = false)

        val adminId = createUserUseCase.createUser("admin@local.dev", "admin")
        userRepository.promoteToSystemAdmin(adminId)
        createOrganizationUseCase.createOrganization("Admin Org", adminId)
        createUserUseCase.createUser("admin-no-org@local.dev", "admin").also { userRepository.promoteToSystemAdmin(it) }

        val devUserId = createUserUseCase.createUser("dev@local.dev", "local")
        assignTierToUserUseCase.assignTierToUser(devUserId, proTier.id)
        val orgId = createOrganizationUseCase.createOrganization("Persönlich", devUserId).id

        val devNoOrgUserId = createUserUseCase.createUser("dev-no-org@local.dev", "local")
        assignTierToUserUseCase.assignTierToUser(devNoOrgUserId, proTier.id)

        createUserUseCase.createUser("standard@local.dev", "local")
        createUserUseCase.createUser("standard-no-org@local.dev", "local")

        val pastDueUserId = createUserUseCase.createUser("pastdue@local.dev", "local")
        assignTierToUserUseCase.assignTierToUser(pastDueUserId, proTier.id)

        val pastDueNoOrgUserId = createUserUseCase.createUser("pastdue-no-org@local.dev", "local")
        assignTierToUserUseCase.assignTierToUser(pastDueNoOrgUserId, proTier.id)

        val canceledUserId = createUserUseCase.createUser("canceled@local.dev", "local")
        val canceledNoOrgUserId = createUserUseCase.createUser("canceled-no-org@local.dev", "local")

        importTransactionsUseCase.importTransactions(
            ImportTransactionsCommand(
                transactions = generateMainTransactions() + generateSavingsTransactions() + generateFixedTransactions(),
                accountNames =
                    mapOf(
                        mainIban to mainName,
                        savingsIban to savingsName,
                    ),
                organizationId = orgId,
                files =
                    listOf(
                        ImportFileSpec(filename = "seed", checksum = "seed", fileType = ImportFileType.CSV, fingerprints = emptyList()),
                    ),
            ),
        )
        setupSuggestedCategories(orgId)
        setupOffsetLinks(orgId)
        setupThresholds(orgId)
        setupBudgets(orgId)
        setupCollections(orgId)
        detectRecurringSeriesUseCase.detect(RefreshRecurringSeriesCommand(organizationId = orgId))

        setupActiveStripeUser(devUserId, idPrefix = "dev", invoicePrefix = "DEV")
        setupActiveStripeUser(devNoOrgUserId, idPrefix = "dev-no-org", invoicePrefix = "DEV-NO-ORG")
        setupPastDueStripeUser(pastDueUserId, idPrefix = "pdu", invoicePrefix = "PDU")
        setupPastDueStripeUser(pastDueNoOrgUserId, idPrefix = "pdu-no-org", invoicePrefix = "PDU-NO-ORG")
        setupCanceledStripeUser(canceledUserId, idPrefix = "can", invoicePrefix = "CAN")
        setupCanceledStripeUser(canceledNoOrgUserId, idPrefix = "can-no-org", invoicePrefix = "CAN-NO-ORG")
    }

    private fun setupActiveStripeUser(
        userId: Long,
        idPrefix: String,
        invoicePrefix: String,
    ) {
        val julFirst = LocalDate.of(2025, 7, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val augFirst = LocalDate.of(2025, 8, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        stripeCustomerRepository.save(
            StripeCustomer(
                id = 0,
                userId = userId,
                stripeCustomerId = "cus_$idPrefix",
                stripeSubscriptionId = "sub_$idPrefix",
                subscriptionStatus = SubscriptionStatus.ACTIVE,
                currentPeriodStart = julFirst,
                currentPeriodEnd = augFirst,
                priceId = "price_local_pro",
            ),
        )
        for (i in 1..6) {
            val start = LocalDate.of(2025, i, 1).atStartOfDay()
            invoiceRepository.save(
                Invoice(
                    id = 0,
                    userId = userId,
                    stripeInvoiceId = "in_${idPrefix}_%03d".format(i),
                    invoiceNumber = "INV-$invoicePrefix-%03d".format(i),
                    amountCents = 999,
                    currency = "eur",
                    status = "paid",
                    periodStart = start,
                    periodEnd = start.plusMonths(1),
                    hasPdf = false,
                    issuedAt = start.plusMonths(1),
                ),
                pdfData = null,
            )
        }
    }

    private fun setupPastDueStripeUser(
        userId: Long,
        idPrefix: String,
        invoicePrefix: String,
    ) {
        val julFirst = LocalDate.of(2025, 7, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val augFirst = LocalDate.of(2025, 8, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        stripeCustomerRepository.save(
            StripeCustomer(
                id = 0,
                userId = userId,
                stripeCustomerId = "cus_$idPrefix",
                stripeSubscriptionId = "sub_$idPrefix",
                subscriptionStatus = SubscriptionStatus.PAST_DUE,
                currentPeriodStart = julFirst,
                currentPeriodEnd = augFirst,
                priceId = "price_local_pro",
            ),
        )
        for (i in 1..3) {
            val start = LocalDate.of(2025, i, 1).atStartOfDay()
            invoiceRepository.save(
                Invoice(
                    id = 0,
                    userId = userId,
                    stripeInvoiceId = "in_${idPrefix}_%03d".format(i),
                    invoiceNumber = "INV-$invoicePrefix-%03d".format(i),
                    amountCents = 999,
                    currency = "eur",
                    status = "paid",
                    periodStart = start,
                    periodEnd = start.plusMonths(1),
                    hasPdf = false,
                    issuedAt = start.plusMonths(1),
                ),
                pdfData = null,
            )
        }
        invoiceRepository.save(
            Invoice(
                id = 0,
                userId = userId,
                stripeInvoiceId = "in_${idPrefix}_004",
                invoiceNumber = "INV-$invoicePrefix-004",
                amountCents = 999,
                currency = "eur",
                status = "open",
                periodStart = LocalDate.of(2025, 7, 1).atStartOfDay(),
                periodEnd = LocalDate.of(2025, 8, 1).atStartOfDay(),
                hasPdf = false,
                issuedAt = LocalDate.of(2025, 7, 1).atStartOfDay(),
            ),
            pdfData = null,
        )
    }

    private fun setupCanceledStripeUser(
        userId: Long,
        idPrefix: String,
        invoicePrefix: String,
    ) {
        stripeCustomerRepository.save(
            StripeCustomer(
                id = 0,
                userId = userId,
                stripeCustomerId = "cus_$idPrefix",
                stripeSubscriptionId = "sub_$idPrefix",
                subscriptionStatus = SubscriptionStatus.CANCELED,
                currentPeriodStart = null,
                currentPeriodEnd = null,
                priceId = null,
            ),
        )
        for (i in 1..2) {
            val start = LocalDate.of(2025, i, 1).atStartOfDay()
            invoiceRepository.save(
                Invoice(
                    id = 0,
                    userId = userId,
                    stripeInvoiceId = "in_${idPrefix}_%03d".format(i),
                    invoiceNumber = "INV-$invoicePrefix-%03d".format(i),
                    amountCents = 999,
                    currency = "eur",
                    status = "paid",
                    periodStart = start,
                    periodEnd = start.plusMonths(1),
                    hasPdf = false,
                    issuedAt = start.plusMonths(1),
                ),
                pdfData = null,
            )
        }
    }

    private fun setupOffsetLinks(orgId: Long) {
        val arztTx = findTx(orgId, ARZT_DATE, "Gesundheit", BigDecimal("-180.00"))
        val erstattungTx = findTx(orgId, ERSTATTUNG_DATE, "Einnahmen", BigDecimal("120.00"))
        if (arztTx != null && erstattungTx != null) {
            manageTransactionOffsetUseCase.linkTransactions(
                LinkTransactionsCommand(
                    transactionId = requireNotNull(arztTx.id),
                    otherTransactionId = requireNotNull(erstattungTx.id),
                    myAmount = BigDecimal("120"),
                    otherAmount = BigDecimal("120"),
                    organizationId = orgId,
                ),
            )
        }

        val restaurantTx = findTx(orgId, RESTAURANT_DATE, "Lebensmittel", BigDecimal("-95.00"))
        val uberweisungTx = findTx(orgId, UEBERWEISUNG_DATE, "Einnahmen", BigDecimal("47.50"))
        if (restaurantTx != null && uberweisungTx != null) {
            manageTransactionOffsetUseCase.linkTransactions(
                LinkTransactionsCommand(
                    transactionId = requireNotNull(restaurantTx.id),
                    otherTransactionId = requireNotNull(uberweisungTx.id),
                    myAmount = BigDecimal("47.50"),
                    otherAmount = BigDecimal("47.50"),
                    organizationId = orgId,
                ),
            )
        }
    }

    private fun setupThresholds(orgId: Long) {
        val lebensmittelId = requireNotNull(categoryRepository.findOrCreate(listOf("Lebensmittel"), orgId).id)
        val transportId = requireNotNull(categoryRepository.findOrCreate(listOf("Transport"), orgId).id)
        val freizeitId = requireNotNull(categoryRepository.findOrCreate(listOf("Freizeit"), orgId).id)

        saveThresholdUseCase.saveThreshold(
            Threshold(
                id = 0,
                categoryId = lebensmittelId,
                categoryPath = listOf("Lebensmittel"),
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("400"),
                warning = BigDecimal("600"),
                critical = BigDecimal("800"),
            ),
            orgId,
        )
        saveThresholdUseCase.saveThreshold(
            Threshold(
                id = 0,
                categoryId = transportId,
                categoryPath = listOf("Transport"),
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("150"),
                warning = BigDecimal("250"),
                critical = null,
            ),
            orgId,
        )
        saveThresholdUseCase.saveThreshold(
            Threshold(
                id = 0,
                categoryId = freizeitId,
                categoryPath = listOf("Freizeit"),
                period = ThresholdPeriod.MONTHLY,
                notice = BigDecimal("80"),
                warning = null,
                critical = null,
            ),
            orgId,
        )
    }

    private fun setupBudgets(orgId: Long) {
        val urlaub =
            createBudgetUseCase.createBudget(
                Budget(name = "Urlaub 2025", targetAmount = BigDecimal("1200")),
                orgId,
            )
        listOf(
            REISE_FLUG_DATE to BigDecimal("-380.00"),
            REISE_HOTEL_DATE to BigDecimal("-490.00"),
            REISE_AKTIVITAETEN_DATE to BigDecimal("-200.00"),
        ).forEach { (date, amount) ->
            findTx(orgId, date, "Reise", amount)?.id?.let { txId ->
                assignTransactionToBudgetUseCase.assignTransaction(requireNotNull(urlaub.id), txId, null, orgId)
            }
        }

        val kueche =
            createBudgetUseCase.createBudget(
                Budget(name = "Neue Küche", targetAmount = BigDecimal("3500")),
                orgId,
            )
        findTx(orgId, WOHNEN_EINRICHTUNG_DATE, "Wohnen", BigDecimal("-800.00"))?.id?.let { txId ->
            assignTransactionToBudgetUseCase.assignTransaction(requireNotNull(kueche.id), txId, BigDecimal("500"), orgId)
        }

        val notfall = createBudgetUseCase.createBudget(Budget(name = "Notfallfonds"), orgId)
        listOf(
            Triple(EINNAHMEN_JAN_20_DATE, "Einnahmen", BigDecimal("500.00")),
            Triple(EINNAHMEN_MAR_5_DATE, "Einnahmen", BigDecimal("400.00")),
            Triple(GESUNDHEIT_APR_DATE, "Gesundheit", BigDecimal("-2300.00")),
            Triple(EINNAHMEN_JUN_10_DATE, "Einnahmen", BigDecimal("700.00")),
            Triple(EINNAHMEN_AUG_DATE, "Einnahmen", BigDecimal("900.00")),
        ).forEach { (date, category, amount) ->
            findTx(orgId, date, category, amount)?.id?.let { txId ->
                assignTransactionToBudgetUseCase.assignTransaction(requireNotNull(notfall.id), txId, null, orgId)
            }
        }
    }

    private fun setupCollections(orgId: Long) {
        val sommer = createCollectionUseCase.createCollection(Collection(name = "Sommer 2025"), orgId)
        val restaurantTxs = queryTxs(orgId, SOMMER_FROM, SOMMER_TO, "Lebensmittel", "Restaurant").take(SOMMER_RESTAURANT_LIMIT)
        val sportTxs = queryTxs(orgId, SOMMER_FROM, SOMMER_TO, "Freizeit", "Sport").take(2)
        (restaurantTxs + sportTxs).forEach { tx ->
            tx.id?.let { manageCollectionMembersUseCase.addTransaction(requireNotNull(sommer.id), it, orgId) }
        }

        val haushalt = createCollectionUseCase.createCollection(Collection(name = "Haushalt Q1 2025"), orgId)
        val mieteTxs = queryTxs(orgId, Q1_FROM, Q1_TO, "Wohnen", "Miete").take(2)
        val internetTxs = queryTxs(orgId, Q1_FROM, Q1_TO, "Wohnen", "Internet").take(2)
        (mieteTxs + internetTxs).forEach { tx ->
            tx.id?.let { manageCollectionMembersUseCase.addTransaction(requireNotNull(haushalt.id), it, orgId) }
        }
    }

    private fun findTx(
        orgId: Long,
        date: LocalDate,
        category: String,
        amount: BigDecimal,
    ): Transaction? =
        getTransactionsUseCase
            .getTransactions(GetTransactionsQuery(from = date, to = date, organizationId = orgId, category = category))
            .find { it.amount.compareTo(amount) == 0 }

    private fun queryTxs(
        orgId: Long,
        from: LocalDate,
        to: LocalDate,
        category: String,
        subcategory: String,
    ): List<Transaction> =
        getTransactionsUseCase.getTransactions(
            GetTransactionsQuery(from = from, to = to, organizationId = orgId, category = category, subcategory = subcategory),
        )

    private fun generateFixedTransactions(): List<Transaction> =
        listOf(
            tx("Gesundheit", "Arzt", ARZT_DATE, BigDecimal("-180.00")),
            tx("Einnahmen", "Erstattung", ERSTATTUNG_DATE, BigDecimal("120.00")),
            tx("Lebensmittel", "Restaurant", RESTAURANT_DATE, BigDecimal("-95.00")),
            tx("Einnahmen", "Überweisung", UEBERWEISUNG_DATE, BigDecimal("47.50")),
            tx("Reise", "Flug", REISE_FLUG_DATE, BigDecimal("-380.00")),
            tx("Reise", "Hotel", REISE_HOTEL_DATE, BigDecimal("-490.00")),
            tx("Reise", "Aktivitäten", REISE_AKTIVITAETEN_DATE, BigDecimal("-200.00")),
            tx("Wohnen", "Einrichtung", WOHNEN_EINRICHTUNG_DATE, BigDecimal("-800.00")),
            tx("Einnahmen", "Sonstiges", EINNAHMEN_JAN_20_DATE, BigDecimal("500.00")),
            tx("Einnahmen", "Sonstiges", EINNAHMEN_MAR_5_DATE, BigDecimal("400.00")),
            tx("Gesundheit", "Behandlung", GESUNDHEIT_APR_DATE, BigDecimal("-2300.00")),
            tx("Einnahmen", "Sonstiges", EINNAHMEN_JUN_10_DATE, BigDecimal("700.00")),
            tx("Einnahmen", "Sonstiges", EINNAHMEN_AUG_DATE, BigDecimal("900.00")),
            txUncategorized(SUGGESTION_REWE_DATE, BigDecimal("-67.30"), "REWE Sagt Danke", "Einkauf REWE 03.12.25"),
            txUncategorized(SUGGESTION_TANKSTELLE_DATE, BigDecimal("-58.40"), "TOTAL Tankstelle", "Tanken TOTAL Hamburg"),
            txUncategorized(SUGGESTION_NETFLIX_DATE, BigDecimal("-17.99"), "NETFLIX.COM", "Netflix Monatsabo"),
            txUncategorized(SUGGESTION_ZALANDO_DATE, BigDecimal("-129.00"), "ZALANDO SE", "Online-Bestellung Zalando"),
            txUncategorized(SUGGESTION_ALLIANZ_DATE, BigDecimal("-189.50"), "Allianz Krankenversich.", "KV-Beitrag Dezember"),
        )

    private fun tx(
        category: String,
        subcategory: String,
        date: LocalDate,
        amount: BigDecimal,
    ) = Transaction(
        category = category,
        subcategory = subcategory,
        group = null,
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = mainIban,
    )

    private fun txUncategorized(
        date: LocalDate,
        amount: BigDecimal,
        counterpartyName: String,
        purpose: String,
    ) = Transaction(
        bookingDate = date,
        valueDate = date,
        accountingDate = date,
        amount = amount,
        currency = "EUR",
        accountIban = mainIban,
        counterpartyName = counterpartyName,
        purpose = purpose,
    )

    private fun setupSuggestedCategories(orgId: Long) {
        val lebensmittelId = requireNotNull(categoryRepository.findOrCreate(listOf("Lebensmittel", "Supermarkt"), orgId).id)
        val transportId = requireNotNull(categoryRepository.findOrCreate(listOf("Transport", "Tankstelle"), orgId).id)
        val streamingId = requireNotNull(categoryRepository.findOrCreate(listOf("Freizeit", "Streaming"), orgId).id)
        val shoppingId = requireNotNull(categoryRepository.findOrCreate(listOf("Shopping", "Kleidung"), orgId).id)
        val versicherungId = requireNotNull(categoryRepository.findOrCreate(listOf("Versicherung", "Krankenversicherung"), orgId).id)

        val suggestions =
            mapOf(
                "REWE Sagt Danke" to lebensmittelId,
                "TOTAL Tankstelle" to transportId,
                "NETFLIX.COM" to streamingId,
                "ZALANDO SE" to shoppingId,
                "Allianz Krankenversich." to versicherungId,
            )

        val uncategorized =
            getTransactionsUseCase.getTransactions(
                GetTransactionsQuery(
                    from = SUGGESTION_FROM,
                    to = SUGGESTION_TO,
                    organizationId = orgId,
                    uncategorized = true,
                ),
            )

        val updates =
            uncategorized.mapNotNull { tx ->
                val categoryId = suggestions[tx.counterpartyName] ?: return@mapNotNull null
                requireNotNull(tx.id) to categoryId
            }

        if (updates.isNotEmpty()) {
            transactionRepository.updateSuggestedCategoryIds(updates)
        }
    }

    private fun generateMainTransactions(): List<Transaction> {
        val rng = Random(MAIN_RNG_SEED)
        val transactions = mutableListOf<Transaction>()

        val months =
            generateSequence(GENERATED_START) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(GENERATED_END) }
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
                counterpartyName: String? = null,
            ) = Transaction(
                category = category,
                subcategory = subcategory,
                group = null,
                bookingDate = date,
                valueDate = date,
                accountingDate = date,
                amount = amount,
                currency = "EUR",
                accountIban = mainIban,
                counterpartyName = counterpartyName,
            )

            transactions += tx("Einnahmen", "Gehalt", day(SALARY_DAY), euros(SALARY_MIN, SALARY_MAX), counterpartyName = "Arbeitgeber GmbH")
            transactions += tx("Wohnen", "Miete", day(1), -euros(RENT_AMOUNT, RENT_AMOUNT), counterpartyName = "Vermieter")
            transactions += tx("Wohnen", "Internet", day(INTERNET_DAY), BigDecimal("-39.99"), counterpartyName = "Telekom")
            transactions += tx("Freizeit", "Streaming", day(STREAMING_DAY), BigDecimal("-17.99"), counterpartyName = "Netflix")
            transactions += tx("Transport", "ÖPNV", day(OPNV_DAY), BigDecimal("-86.00"), counterpartyName = "MVG")
            transactions +=
                tx("Versicherung", "Krankenversicherung", day(INSURANCE_DAY), BigDecimal("-189.50"), counterpartyName = "Allianz")
            transactions += tx("Freizeit", "Sport", day(GYM_DAY), BigDecimal("-29.90"), counterpartyName = "FitnessFabrik")
            transactions += tx("Freizeit", "Streaming", day(MUSIC_DAY), BigDecimal("-10.99"), counterpartyName = "Spotify")

            repeat(rng.nextInt(SUPERMARKT_MAX_REPEAT) + 2) {
                transactions += tx("Lebensmittel", "Supermarkt", randomDay(), -euros(SUPERMARKT_MIN, SUPERMARKT_MAX))
            }
            repeat(rng.nextInt(RESTAURANT_MAX_REPEAT) + 1) {
                transactions += tx("Lebensmittel", "Restaurant", randomDay(), -euros(RESTAURANT_MIN, RESTAURANT_MAX))
            }

            if (rng.nextDouble() <
                APOTHEKE_PROBABILITY
            ) {
                transactions += tx("Gesundheit", "Apotheke", randomDay(), -euros(APOTHEKE_MIN, APOTHEKE_MAX))
            }
            if (rng.nextDouble() <
                TANKSTELLE_PROBABILITY
            ) {
                transactions += tx("Transport", "Tankstelle", randomDay(), -euros(TANKSTELLE_MIN, TANKSTELLE_MAX))
            }
            if (rng.nextDouble() <
                KLEIDUNG_PROBABILITY
            ) {
                transactions += tx("Shopping", "Kleidung", randomDay(), -euros(KLEIDUNG_MIN, KLEIDUNG_MAX))
            }
            if (rng.nextDouble() < SPORT_PROBABILITY) transactions += tx("Freizeit", "Sport", randomDay(), -euros(SPORT_MIN, SPORT_MAX))
            if (rng.nextDouble() < ARZT_PROBABILITY) transactions += tx("Gesundheit", "Arzt", randomDay(), -euros(ARZT_MIN, ARZT_MAX))
            if (rng.nextDouble() <
                ELEKTRONIK_PROBABILITY
            ) {
                transactions += tx("Shopping", "Elektronik", randomDay(), -euros(ELEKTRONIK_MIN, ELEKTRONIK_MAX))
            }
        }

        return transactions.sortedBy { it.bookingDate }
    }

    private fun generateSavingsTransactions(): List<Transaction> {
        val rng = Random(SAVINGS_RNG_SEED)
        val transactions = mutableListOf<Transaction>()

        val months =
            generateSequence(GENERATED_START) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(GENERATED_END) }
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
                group = null,
                bookingDate = date,
                valueDate = date,
                accountingDate = date,
                amount = amount,
                currency = "EUR",
                accountIban = savingsIban,
            )

            transactions += tx("Einnahmen", "Zinsen", day(1), euros(2.0, ZINSEN_MAX))
            transactions += tx("Sparen", "Einzahlung", day(SPAREINZAHLUNG_DAY), -euros(SPAREINZAHLUNG_MIN, SPAREINZAHLUNG_MAX))

            if (rng.nextDouble() <
                ENTNAHME_PROBABILITY
            ) {
                transactions += tx("Sparen", "Entnahme", randomDay(), euros(ENTNAHME_MIN, ENTNAHME_MAX))
            }
            if (rng.nextDouble() <
                JAHRESBEITRAG_PROBABILITY
            ) {
                transactions +=
                    tx("Versicherung", "Jahresbeitrag", randomDay(), -euros(JAHRESBEITRAG_MIN, JAHRESBEITRAG_MAX))
            }
        }

        return transactions.sortedBy { it.bookingDate }
    }
}
