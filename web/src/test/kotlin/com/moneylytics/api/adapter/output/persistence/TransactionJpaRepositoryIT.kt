package com.moneylytics.api.adapter.output.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionJpaRepositoryIT : AbstractJpaRepositoryIT() {
    private val jan1 = LocalDate.of(2025, 1, 1)
    private val jan15 = LocalDate.of(2025, 1, 15)
    private val jan31 = LocalDate.of(2025, 1, 31)
    private val dec31 = LocalDate.of(2024, 12, 31)
    private val feb1 = LocalDate.of(2025, 2, 1)

    @Test
    fun `should find transactions by user id and date range`() {
        savedTransaction("fp-1", accountingDate = jan15)
        savedTransaction("fp-2", accountingDate = dec31)
        savedTransaction("fp-3", accountingDate = feb1)

        val result = transactionRepo.findByOrganizationIdAndAccountingDateBetween(organizationId, jan1, jan31)

        assertThat(result).hasSize(1)
        assertThat(result.first().fingerprint).isEqualTo("fp-1")
    }

    @Test
    fun `should not return transactions of other user in date range`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", organization = otherOrganization))
        savedTransaction("fp-1", accountingDate = jan15)
        savedTransaction("fp-2", accountingDate = jan15, forAccount = otherAccount, forOrganization = otherOrganization)

        val result = transactionRepo.findByOrganizationIdAndAccountingDateBetween(organizationId, jan1, jan31)

        assertThat(result).hasSize(1)
        assertThat(result.first().fingerprint).isEqualTo("fp-1")
    }

    @Test
    fun `should find negative transactions by user id and date range`() {
        savedTransaction("fp-expense", accountingDate = jan15, amount = BigDecimal("-50.00"))
        savedTransaction("fp-income", accountingDate = jan15, amount = BigDecimal("1000.00"))

        val result =
            transactionRepo.findByOrganizationIdAndAccountingDateBetweenAndAmountLessThan(
                organizationId = organizationId,
                from = jan1,
                to = jan31,
                amount = BigDecimal.ZERO,
            )

        assertThat(result).hasSize(1)
        assertThat(result.first().fingerprint).isEqualTo("fp-expense")
    }

    @Test
    fun `should find transactions by iban and date range`() {
        val secondAccount = accountRepo.save(AccountEntity(iban = "DE00TEST000000000002", name = "Sparkonto", organization = organization))
        savedTransaction("fp-1", accountingDate = jan15, forAccount = account)
        savedTransaction("fp-2", accountingDate = jan15, forAccount = secondAccount)

        val result =
            transactionRepo.findByOrganizationIdAndAccountIbanAndAccountingDateBetween(
                organizationId = organizationId,
                iban = account.iban,
                from = jan1,
                to = jan31,
            )

        assertThat(result).hasSize(1)
        assertThat(result.first().fingerprint).isEqualTo("fp-1")
    }

    @Test
    fun `should find negative transactions by iban and date range`() {
        val secondAccount = accountRepo.save(AccountEntity(iban = "DE00TEST000000000002", name = "Sparkonto", organization = organization))
        savedTransaction("fp-expense", accountingDate = jan15, amount = BigDecimal("-25.00"), forAccount = account)
        savedTransaction("fp-income", accountingDate = jan15, amount = BigDecimal("500.00"), forAccount = account)
        savedTransaction("fp-other", accountingDate = jan15, amount = BigDecimal("-25.00"), forAccount = secondAccount)

        val result =
            transactionRepo.findByOrganizationIdAndAccountIbanAndAccountingDateBetweenAndAmountLessThan(
                organizationId = organizationId,
                iban = account.iban,
                from = jan1,
                to = jan31,
                amount = BigDecimal.ZERO,
            )

        assertThat(result).hasSize(1)
        assertThat(result.first().fingerprint).isEqualTo("fp-expense")
    }

    @Test
    fun `should find transaction by id and user id`() {
        val tx = savedTransaction("fp-1")
        val txId = checkNotNull(tx.id)

        val result = transactionRepo.findByIdAndOrganizationId(txId, organizationId)

        assertThat(result).isNotNull
        assertThat(result?.fingerprint).isEqualTo("fp-1")
    }

    @Test
    fun `should return null when transaction belongs to different user`() {
        val tx = savedTransaction("fp-1")
        val txId = checkNotNull(tx.id)

        val result = transactionRepo.findByIdAndOrganizationId(txId, otherOrganizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should return only existing fingerprints from given set`() {
        savedTransaction("fp-exists-1")
        savedTransaction("fp-exists-2")

        val result =
            transactionRepo.findExistingFingerprints(
                fingerprints = listOf("fp-exists-1", "fp-exists-2", "fp-not-stored"),
                organizationId = organizationId,
            )

        assertThat(result).containsExactlyInAnyOrder("fp-exists-1", "fp-exists-2")
    }

    @Test
    fun `should not return fingerprints belonging to other user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", organization = otherOrganization))
        savedTransaction("fp-mine")
        savedTransaction("fp-theirs", forAccount = otherAccount, forOrganization = otherOrganization)

        val result =
            transactionRepo.findExistingFingerprints(
                fingerprints = listOf("fp-mine", "fp-theirs"),
                organizationId = organizationId,
            )

        assertThat(result).containsExactly("fp-mine")
    }

    @Test
    fun `should find transactions by ids for user`() {
        val tx1 = savedTransaction("fp-1")
        val tx2 = savedTransaction("fp-2")
        val tx1Id = checkNotNull(tx1.id)
        val tx2Id = checkNotNull(tx2.id)

        val result = transactionRepo.findByIdsAndOrganizationId(listOf(tx1Id, tx2Id), organizationId)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.fingerprint }).containsExactlyInAnyOrder("fp-1", "fp-2")
    }

    @Test
    fun `should not return transaction by ids belonging to other user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", organization = otherOrganization))
        val tx = savedTransaction("fp-theirs", forAccount = otherAccount, forOrganization = otherOrganization)
        val txId = checkNotNull(tx.id)

        val result = transactionRepo.findByIdsAndOrganizationId(listOf(txId), organizationId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should find transaction by fingerprint and user id`() {
        savedTransaction("fp-target")

        val result = transactionRepo.findByFingerprintAndOrganizationId("fp-target", organizationId)

        assertThat(result).isNotNull
        assertThat(result?.fingerprint).isEqualTo("fp-target")
    }

    @Test
    fun `should return null for fingerprint belonging to other user`() {
        val otherAccount = accountRepo.save(AccountEntity(iban = "DE00OTHER00000000002", name = "Fremd", organization = otherOrganization))
        savedTransaction("fp-theirs", forAccount = otherAccount, forOrganization = otherOrganization)

        val result = transactionRepo.findByFingerprintAndOrganizationId("fp-theirs", organizationId)

        assertThat(result).isNull()
    }

    @Test
    fun `should return latest accounting date per iban`() {
        val secondAccount = accountRepo.save(AccountEntity(iban = "DE00TEST000000000002", name = "Sparkonto", organization = organization))
        savedTransaction("fp-a-early", accountingDate = LocalDate.of(2025, 1, 5), forAccount = account)
        savedTransaction("fp-a-late", accountingDate = LocalDate.of(2025, 3, 20), forAccount = account)
        savedTransaction("fp-b", accountingDate = LocalDate.of(2025, 2, 10), forAccount = secondAccount)

        val result = transactionRepo.findLatestDatePerIban(organizationId)

        assertThat(result).hasSize(2)
        val resultMap = result.associate { row -> (row[0] as String) to (row[1] as LocalDate) }
        assertThat(resultMap[account.iban]).isEqualTo(LocalDate.of(2025, 3, 20))
        assertThat(resultMap[secondAccount.iban]).isEqualTo(LocalDate.of(2025, 2, 10))
    }
}
