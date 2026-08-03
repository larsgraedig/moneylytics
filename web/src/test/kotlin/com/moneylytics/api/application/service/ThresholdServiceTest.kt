package com.moneylytics.api.application.service

import com.moneylytics.api.application.port.input.GetThresholdStatusQuery
import com.moneylytics.api.application.port.output.CategoryRepository
import com.moneylytics.api.application.port.output.ThresholdRepository
import com.moneylytics.api.application.port.output.TransactionRepository
import com.moneylytics.api.domain.Category
import com.moneylytics.api.domain.Threshold
import com.moneylytics.api.domain.ThresholdPeriod
import com.moneylytics.api.domain.ThresholdStatus
import com.moneylytics.api.domain.Transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class ThresholdServiceTest {
    private val thresholdRepository: ThresholdRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val service = ThresholdService(thresholdRepository, transactionRepository, categoryRepository)

    private val organizationId = 1L
    private val jan1 = LocalDate.of(2025, 1, 1)
    private val jan31 = LocalDate.of(2025, 1, 31)
    private val feb28 = LocalDate.of(2025, 2, 28)

    // Category IDs used across tests
    private val lebensmittelId = 1L
    private val supermarktId = 2L
    private val restaurantId = 3L
    private val transportId = 4L

    private val flatCategories =
        listOf(
            Category(id = lebensmittelId, name = "Lebensmittel", parentId = null),
            Category(id = supermarktId, name = "Supermarkt", parentId = lebensmittelId),
            Category(id = restaurantId, name = "Restaurant", parentId = lebensmittelId),
            Category(id = transportId, name = "Transport", parentId = null),
        )

    @Test
    fun `should return empty response when no thresholds exist`() {
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(emptyList())

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        assertThat(result.items).isEmpty()
    }

    @Test
    fun `should skip transactions without categoryId`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, notice = "80", critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(listOf(tx(null, "-50.00")))

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        assertThat(result.items[0].spending).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `should aggregate spending from child categories into parent threshold`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(
                listOf(
                    tx(supermarktId, "-80.00"),
                    tx(restaurantId, "-45.00"),
                ),
            )

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        assertThat(result.items[0].spending).isEqualByComparingTo(BigDecimal("125.00"))
    }

    @Test
    fun `should count leaf category spending only in its own threshold`() {
        val thresholdOnLeaf = threshold(id = 1L, categoryId = supermarktId, critical = "100")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(thresholdOnLeaf))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(
                listOf(
                    tx(supermarktId, "-60.00"),
                    tx(restaurantId, "-45.00"),
                ),
            )

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        // Supermarkt threshold should only count supermarkt spending, not restaurant
        assertThat(result.items[0].spending).isEqualByComparingTo(BigDecimal("60.00"))
    }

    @Test
    fun `should set status OK when spending is below notice threshold`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, notice = "100", warning = "150", critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(listOf(tx(lebensmittelId, "-50.00")))

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        assertThat(result.items[0].status).isEqualTo(ThresholdStatus.OK)
    }

    @Test
    fun `should set status NOTICE when spending reaches notice but not warning`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, notice = "100", warning = "150", critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(listOf(tx(lebensmittelId, "-110.00")))

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        assertThat(result.items[0].status).isEqualTo(ThresholdStatus.NOTICE)
    }

    @Test
    fun `should set status WARNING when spending reaches warning but not critical`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, notice = "100", warning = "150", critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(listOf(tx(lebensmittelId, "-160.00")))

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        assertThat(result.items[0].status).isEqualTo(ThresholdStatus.WARNING)
    }

    @Test
    fun `should set status CRITICAL when spending reaches critical threshold`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, notice = "100", warning = "150", critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(listOf(tx(lebensmittelId, "-210.00")))

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        assertThat(result.items[0].status).isEqualTo(ThresholdStatus.CRITICAL)
    }

    @Test
    fun `should scale monthly threshold by number of months in range`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, notice = "100", critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, feb28, organizationId, null))
            .thenReturn(listOf(tx(lebensmittelId, "-180.00")))

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, feb28, organizationId))

        // 2 months: notice = 200, critical = 400 → spending 180 < 200 → OK
        assertThat(result.items[0].status).isEqualTo(ThresholdStatus.OK)
        assertThat(result.items[0].periodsInRange).isEqualByComparingTo(BigDecimal("2"))
    }

    @Test
    fun `should pick weekly threshold for 7-day range`() {
        val weekly = threshold(id = 1L, categoryId = transportId, critical = "50", period = ThresholdPeriod.WEEKLY)
        val monthly = threshold(id = 2L, categoryId = transportId, critical = "200", period = ThresholdPeriod.MONTHLY)
        val from = LocalDate.of(2025, 1, 1)
        val to = LocalDate.of(2025, 1, 7)
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(weekly, monthly))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(from, to, organizationId, null))
            .thenReturn(emptyList())

        val result = service.getThresholdStatus(GetThresholdStatusQuery(from, to, organizationId))

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].period).isEqualTo(ThresholdPeriod.WEEKLY)
    }

    @Test
    fun `should compute tick positions for notice and warning levels`() {
        val threshold = threshold(id = 1L, categoryId = lebensmittelId, notice = "80", warning = "120", critical = "200")
        whenever(thresholdRepository.findAllByOrganizationId(organizationId)).thenReturn(listOf(threshold))
        whenever(categoryRepository.findAll(organizationId)).thenReturn(flatCategories)
        whenever(transactionRepository.findNegativeByAccountingDateBetween(jan1, jan31, organizationId, null))
            .thenReturn(listOf(tx(lebensmittelId, "-50.00")))

        val result = service.getThresholdStatus(GetThresholdStatusQuery(jan1, jan31, organizationId))

        val item = result.items[0]
        assertThat(item.tickNotice).isEqualByComparingTo(BigDecimal("0.4")) // 80/200
        assertThat(item.tickWarning).isEqualByComparingTo(BigDecimal("0.6")) // 120/200
    }

    private fun threshold(
        id: Long,
        categoryId: Long,
        notice: String? = null,
        warning: String? = null,
        critical: String? = null,
        period: ThresholdPeriod = ThresholdPeriod.MONTHLY,
    ) = Threshold(
        id = id,
        categoryId = categoryId,
        categoryPath = emptyList(),
        period = period,
        notice = notice?.let { BigDecimal(it) },
        warning = warning?.let { BigDecimal(it) },
        critical = critical?.let { BigDecimal(it) },
    )

    private fun tx(
        categoryId: Long?,
        amount: String,
    ) = Transaction(
        categoryId = categoryId,
        bookingDate = jan1,
        valueDate = jan1,
        accountingDate = jan1,
        amount = BigDecimal(amount),
        currency = "EUR",
        accountIban = "DE00TEST",
    )
}
