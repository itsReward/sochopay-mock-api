package com.soshopay.mockapi.storage

import com.soshopay.mockapi.models.*
import com.soshopay.mockapi.workflows.LoanApplicationStatus
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class LoanStore(
    val loans: MutableMap<String, Loan> = mutableMapOf(),
    val applications: MutableMap<String, LoanApplication> = mutableMapOf(),
    val nextLoanId: Int = 1,
    val nextApplicationId: Int = 1
)

@Serializable
data class Loan(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("application_id") val applicationId: String,
    @SerialName("loan_type") val loanType: String,
    @SerialName("original_amount") val originalAmount: Double,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("remaining_balance") val remainingBalance: Double,
    @SerialName("interest_rate") val interestRate: Double,
    @SerialName("repayment_period") val repaymentPeriod: String,
    @SerialName("disbursement_date") val disbursementDate: Long,
    @SerialName("maturity_date") val maturityDate: Long,
    val status: String,
    @SerialName("next_payment_date") val nextPaymentDate: Long?,
    @SerialName("next_payment_amount") val nextPaymentAmount: Double?,
    @SerialName("payments_completed") val paymentsCompleted: Int,
    @SerialName("total_payments") val totalPayments: Int,
    @SerialName("product_name") val productName: String?,
    @SerialName("loan_purpose") val loanPurpose: String?,
    @SerialName("installation_date") val installationDate: Long?,
    @SerialName("rejection_reason") val rejectionReason: String?,
    @SerialName("rejection_date") val rejectionDate: Long?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class LoanApplication(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("loan_type") val loanType: String,
    @SerialName("loan_amount") val loanAmount: Double,
    @SerialName("repayment_period") val repaymentPeriod: String,
    val status: String,
    @SerialName("submitted_at") val submittedAt: Long?,
    @SerialName("review_started_at") val reviewStartedAt: Long?,
    @SerialName("review_completed_at") val reviewCompletedAt: Long?,
    @SerialName("approved_at") val approvedAt: Long?,
    @SerialName("rejection_reason") val rejectionReason: String?,
    @SerialName("loan_purpose") val loanPurpose: String?,
    @SerialName("product_name") val productName: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

class LoanStorage {
    private val storage = JsonFileStorage<LoanStore>(
        fileName = "loans.json",
        defaultValue = LoanStore()
    )

    suspend fun createApplication(
        userId: String,
        loanType: String,
        loanAmount: Double,
        repaymentPeriod: String,
        loanPurpose: String?,
        productName: String?
    ): LoanApplication {
        val store = storage.read<LoanStore>()
        val id = "APP${store.nextApplicationId}"
        val now = Clock.System.now()

        val application = LoanApplication(
            id = id,
            userId = userId,
            loanType = loanType,
            loanAmount = loanAmount,
            repaymentPeriod = repaymentPeriod,
            status = LoanApplicationStatus.SUBMITTED.name,
            submittedAt = now.toEpochMilliseconds(),
            reviewStartedAt = null,
            reviewCompletedAt = null,
            approvedAt = null,
            rejectionReason = null,
            loanPurpose = loanPurpose,
            productName = productName,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        storage.write(store.copy(
            applications = store.applications.apply { put(id, application) },
            nextApplicationId = store.nextApplicationId + 1
        ))

        return application
    }

    suspend fun updateApplicationStatus(
        applicationId: String,
        status: LoanApplicationStatus,
        rejectionReason: String? = null
    ) {
        storage.update<LoanStore> { store ->
            val application = store.applications[applicationId]
            if (application != null) {
                val updated = application.copy(
                    status = status.name,
                    rejectionReason = rejectionReason,
                    reviewCompletedAt = if (status == LoanApplicationStatus.APPROVED ||
                        status == LoanApplicationStatus.REJECTED)
                        Clock.System.now().toEpochMilliseconds() else null,
                    approvedAt = if (status == LoanApplicationStatus.APPROVED)
                        Clock.System.now().toEpochMilliseconds() else null,
                    updatedAt = Clock.System.now().toString()
                )
                store.applications[applicationId] = updated
            }
            store
        }
    }

    suspend fun createLoanFromApplication(application: LoanApplication): Loan {
        val store = storage.read<LoanStore>()
        val id = "LOAN${store.nextLoanId}"
        val now = Clock.System.now()

        // Calculate loan terms
        val interestRate = calculateInterestRate(application.loanType, application.loanAmount)
        val totalAmount = application.loanAmount * (1 + interestRate)
        val totalPayments = calculateTotalPayments(application.repaymentPeriod)
        val paymentAmount = totalAmount / totalPayments

        val loan = Loan(
            id = id,
            userId = application.userId,
            applicationId = application.id,
            loanType = application.loanType,
            originalAmount = application.loanAmount,
            totalAmount = totalAmount,
            remainingBalance = totalAmount,
            interestRate = interestRate,
            repaymentPeriod = application.repaymentPeriod,
            disbursementDate = now.toEpochMilliseconds(),
            maturityDate = calculateMaturityDate(now.toEpochMilliseconds(), application.repaymentPeriod),
            status = "ACTIVE",
            nextPaymentDate = calculateNextPaymentDate(now.toEpochMilliseconds()),
            nextPaymentAmount = paymentAmount,
            paymentsCompleted = 0,
            totalPayments = totalPayments,
            productName = application.productName,
            loanPurpose = application.loanPurpose,
            installationDate = if (application.loanType == "PAYGO") now.toEpochMilliseconds() else null,
            rejectionReason = null,
            rejectionDate = null,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        storage.write(store.copy(
            loans = store.loans.apply { put(id, loan) },
            nextLoanId = store.nextLoanId + 1
        ))

        return loan
    }

    suspend fun findLoansByUserId(userId: String): List<Loan> {
        val store = storage.read<LoanStore>()
        return store.loans.values.filter { it.userId == userId }
    }

    suspend fun findLoanById(loanId: String): Loan? {
        val store = storage.read<LoanStore>()
        return store.loans[loanId]
    }

    suspend fun findApplicationById(applicationId: String): LoanApplication? {
        val store = storage.read<LoanStore>()
        return store.applications[applicationId]
    }

    suspend fun updateLoanBalance(loanId: String, paymentAmount: Double) {
        storage.update<LoanStore> { store ->
            val loan = store.loans[loanId]
            if (loan != null) {
                val newBalance = loan.remainingBalance - paymentAmount
                val paymentsCompleted = loan.paymentsCompleted + 1

                val updated = loan.copy(
                    remainingBalance = if (newBalance < 0) 0.0 else newBalance,
                    paymentsCompleted = paymentsCompleted,
                    status = if (newBalance <= 0) "COMPLETED" else "ACTIVE",
                    nextPaymentDate = if (newBalance > 0)
                        calculateNextPaymentDate(Clock.System.now().toEpochMilliseconds())
                    else null,
                    updatedAt = Clock.System.now().toString()
                )
                store.loans[loanId] = updated
            }
            store
        }
    }

    private fun calculateInterestRate(loanType: String, amount: Double): Double {
        return when (loanType) {
            "CASH" -> when {
                amount < 10000 -> 0.15  // 15%
                amount < 50000 -> 0.12  // 12%
                else -> 0.10            // 10%
            }
            "PAYGO" -> 0.18             // 18% for PayGo
            else -> 0.15
        }
    }

    private fun calculateTotalPayments(repaymentPeriod: String): Int {
        return when (repaymentPeriod) {
            "3_MONTHS" -> 3
            "6_MONTHS" -> 6
            "12_MONTHS" -> 12
            "18_MONTHS" -> 18
            "24_MONTHS" -> 24
            else -> 12
        }
    }

    private fun calculateMaturityDate(disbursementDate: Long, repaymentPeriod: String): Long {
        val months = calculateTotalPayments(repaymentPeriod)
        return disbursementDate + (months * 30L * 24 * 60 * 60 * 1000) // Rough calculation
    }

    private fun calculateNextPaymentDate(currentDate: Long): Long {
        return currentDate + (30L * 24 * 60 * 60 * 1000) // 30 days from now
    }
}