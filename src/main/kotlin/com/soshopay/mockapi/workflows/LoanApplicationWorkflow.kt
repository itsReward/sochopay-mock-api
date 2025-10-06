package com.soshopay.mockapi.workflows

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import java.util.*

enum class LoanApplicationStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED,
    DISBURSED
}

data class LoanApplicationState(
    val applicationId: String,
    val status: LoanApplicationStatus,
    val submittedAt: Long? = null,
    val reviewStartedAt: Long? = null,
    val reviewCompletedAt: Long? = null,
    val approvedAt: Long? = null,
    val disbursedAt: Long? = null,
    val rejectionReason: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

class LoanApplicationWorkflow {

    /**
     * Simulate realistic loan approval workflow with delays
     */
    suspend fun processApplication(
        applicationId: String,
        loanType: String,
        amount: Double,
        clientVerificationStatus: String
    ): LoanApplicationState {
        val now = Clock.System.now().toEpochMilliseconds()

        // Initial state
        var state = LoanApplicationState(
            applicationId = applicationId,
            status = LoanApplicationStatus.SUBMITTED,
            submittedAt = now
        )

        // Simulate submission delay
        delay(1000)

        // Move to UNDER_REVIEW
        state = state.copy(
            status = LoanApplicationStatus.UNDER_REVIEW,
            reviewStartedAt = Clock.System.now().toEpochMilliseconds()
        )

        // Simulate review process (2-5 seconds)
        delay((2000..5000).random().toLong())

        // Decision logic based on various factors
        val isApproved = determineApproval(
            loanType = loanType,
            amount = amount,
            verificationStatus = clientVerificationStatus
        )

        state = if (isApproved) {
            state.copy(
                status = LoanApplicationStatus.APPROVED,
                reviewCompletedAt = Clock.System.now().toEpochMilliseconds(),
                approvedAt = Clock.System.now().toEpochMilliseconds()
            )
        } else {
            state.copy(
                status = LoanApplicationStatus.REJECTED,
                reviewCompletedAt = Clock.System.now().toEpochMilliseconds(),
                rejectionReason = generateRejectionReason(amount, clientVerificationStatus)
            )
        }

        return state
    }

    /**
     * Simulate loan disbursement
     */
    suspend fun disburseLoan(applicationId: String): LoanApplicationState {
        // Simulate disbursement delay
        delay(2000)

        return LoanApplicationState(
            applicationId = applicationId,
            status = LoanApplicationStatus.DISBURSED,
            disbursedAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    private fun determineApproval(
        loanType: String,
        amount: Double,
        verificationStatus: String
    ): Boolean {
        // Realistic approval logic
        return when {
            verificationStatus != "VERIFIED" -> false
            loanType == "CASH" && amount > 50000 -> (0..100).random() > 30 // 70% approval
            loanType == "PAYGO" && amount > 100000 -> (0..100).random() > 20 // 80% approval
            else -> (0..100).random() > 10 // 90% approval for small amounts
        }
    }

    private fun generateRejectionReason(amount: Double, verificationStatus: String): String {
        return when {
            verificationStatus != "VERIFIED" -> "Profile verification incomplete"
            amount > 100000 -> "Requested amount exceeds maximum limit for your profile"
            else -> "Unable to approve at this time. Please contact support."
        }
    }
}