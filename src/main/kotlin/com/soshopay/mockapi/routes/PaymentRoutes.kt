package com.soshopay.mockapi.routes

import com.soshopay.mockapi.models.PaymentRequest
import com.soshopay.mockapi.storage.*
import com.soshopay.mockapi.workflows.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun Route.paymentRoutes(
    paymentStorage: PaymentStorage,
    loanStorage: LoanStorage,
    paymentWorkflow: PaymentProcessingWorkflow
) {
    route("/api/payments") {

        authenticate("auth-jwt") {

            // Get Payment Dashboard
            get("/dashboard") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val loans = loanStorage.findLoansByUserId(userId)
                val payments = paymentStorage.findPaymentsByUserId(userId)

                val activeLoans = loans.filter { it.status == "ACTIVE" }
                val totalOutstanding = activeLoans.sumOf { it.remainingBalance }
                val nextPayment = activeLoans.minByOrNull { it.nextPaymentDate ?: Long.MAX_VALUE }
                val recentPayments = payments.sortedByDescending { it.createdAt }.take(5)

                call.respond(HttpStatusCode.OK, mapOf(
                    "total_outstanding" to totalOutstanding,
                    "active_loans_count" to activeLoans.size,
                    "next_payment" to if (nextPayment != null) mapOf(
                        "loan_id" to nextPayment.id,
                        "amount" to nextPayment.nextPaymentAmount,
                        "due_date" to nextPayment.nextPaymentDate
                    ) else null,
                    "recent_payments" to recentPayments,
                    "payment_summary" to activeLoans.map { loan ->
                        mapOf(
                            "loan_id" to loan.id,
                            "product_name" to loan.productName,
                            "amount_due" to loan.nextPaymentAmount,
                            "due_date" to loan.nextPaymentDate,
                            "status" to if (Clock.System.now().toEpochMilliseconds() > (loan.nextPaymentDate ?: Long.MAX_VALUE)) "OVERDUE" else "CURRENT"
                        )
                    }
                ))
            }

            // Get Payment History
            get("/history") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val payments = paymentStorage.findPaymentsByUserId(userId)
                    .sortedByDescending { it.createdAt }

                val total = payments.size
                val startIndex = (page - 1) * limit
                val endIndex = minOf(startIndex + limit, total)
                val paginatedPayments = if (startIndex < total) payments.subList(startIndex, endIndex) else emptyList()

                call.respond(HttpStatusCode.OK, mapOf(
                    "payments" to paginatedPayments,
                    "pagination" to mapOf(
                        "page" to page,
                        "limit" to limit,
                        "total" to total,
                        "total_pages" to ((total + limit - 1) / limit)
                    )
                ))
            }

            // Get Available Payment Methods
            get("/methods") {
                call.respond(HttpStatusCode.OK, mapOf(
                    "methods" to listOf(
                        mapOf(
                            "id" to "ECOCASH",
                            "name" to "EcoCash",
                            "type" to "MOBILE_MONEY",
                            "logo_url" to "https://example.com/ecocash.png",
                            "min_amount" to 100.0,
                            "max_amount" to 500000.0,
                            "processing_fee" to 0.0,
                            "is_available" to true
                        ),
                        mapOf(
                            "id" to "ONEMONEY",
                            "name" to "OneMoney",
                            "type" to "MOBILE_MONEY",
                            "logo_url" to "https://example.com/onemoney.png",
                            "min_amount" to 100.0,
                            "max_amount" to 500000.0,
                            "processing_fee" to 0.0,
                            "is_available" to true
                        ),
                        mapOf(
                            "id" to "TELECASH",
                            "name" to "TeleCash",
                            "type" to "MOBILE_MONEY",
                            "logo_url" to "https://example.com/telecash.png",
                            "min_amount" to 100.0,
                            "max_amount" to 500000.0,
                            "processing_fee" to 0.0,
                            "is_available" to true
                        )
                    )
                ))
            }

            // Process Payment
            post("/process") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<PaymentRequest>()

                // Validate loan
                val loan = loanStorage.findLoanById(request.loanId)
                if (loan == null || loan.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Loan not found"
                    ))
                    return@post
                }

                if (loan.status != "ACTIVE") {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "message" to "Loan is not active"
                    ))
                    return@post
                }

                // Create payment
                val payment = paymentStorage.createPayment(
                    userId = userId,
                    loanId = request.loanId,
                    amount = request.amount,
                    method = request.paymentMethod,
                    phoneNumber = request.phoneNumber
                )

                // Process payment asynchronously
                launch {
                    val result = paymentWorkflow.processPayment(
                        paymentId = payment.id,
                        amount = request.amount,
                        phoneNumber = request.phoneNumber,
                        method = request.paymentMethod
                    )

                    // Update payment status
                    paymentStorage.updatePaymentStatus(
                        paymentId = payment.id,
                        status = result.status,
                        transactionReference = result.transactionReference,
                        receiptNumber = result.receiptNumber,
                        failureReason = result.failureReason
                    )

                    // If successful, update loan balance
                    if (result.status == PaymentStatus.SUCCESSFUL) {
                        loanStorage.updateLoanBalance(request.loanId, request.amount)
                    }
                }

                call.respond(HttpStatusCode.Created, mapOf(
                    "payment_id" to payment.id,
                    "status" to "PENDING",
                    "message" to "Payment is being processed",
                    "transaction_reference" to payment.paymentId,
                    "estimated_processing_time" to "3-10 seconds"
                ))
            }

            // Get Payment Status
            get("/{paymentId}/status") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val paymentId = call.parameters["paymentId"]!!

                val payment = paymentStorage.findPaymentById(paymentId)

                if (payment == null || payment.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Payment not found"
                    ))
                    return@get
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "payment_id" to payment.id,
                    "status" to payment.status,
                    "message" to when (payment.status) {
                        "SUCCESSFUL" -> "Payment completed successfully"
                        "FAILED" -> payment.failureReason ?: "Payment failed"
                        "PROCESSING" -> "Payment is being processed"
                        else -> "Payment is pending"
                    },
                    "receipt_number" to payment.receiptNumber,
                    "transaction_reference" to payment.transactionReference,
                    "failure_reason" to payment.failureReason
                ))
            }

            // Download Receipt
            get("/receipts/{receiptNumber}/download") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val receiptNumber = call.parameters["receiptNumber"]!!

                val payments = paymentStorage.findPaymentsByUserId(userId)
                val payment = payments.find { it.receiptNumber == receiptNumber }

                if (payment == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Receipt not found"
                    ))
                    return@get
                }

                // Mock PDF receipt
                val receiptContent = """
                    PAYMENT RECEIPT
                    ================
                    Receipt #: ${payment.receiptNumber}
                    Payment ID: ${payment.paymentId}
                    Amount: $${payment.amount}
                    Method: ${payment.method}
                    Date: ${payment.createdAt}
                    Status: ${payment.status}
                """.trimIndent().toByteArray()

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"receipt_${payment.receiptNumber}.pdf\""
                )
                call.respondBytes(receiptContent, ContentType.Application.Pdf)
            }

            // Get Payment Receipt Details
            get("/receipts/{receiptNumber}") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val receiptNumber = call.parameters["receiptNumber"]!!

                val payments = paymentStorage.findPaymentsByUserId(userId)
                val payment = payments.find { it.receiptNumber == receiptNumber }

                if (payment == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Receipt not found"
                    ))
                    return@get
                }

                val loan = loanStorage.findLoanById(payment.loanId)

                call.respond(HttpStatusCode.OK, mapOf(
                    "receipt_number" to payment.receiptNumber,
                    "payment_id" to payment.paymentId,
                    "loan_id" to payment.loanId,
                    "amount" to payment.amount,
                    "payment_method" to payment.method,
                    "phone_number" to payment.phoneNumber,
                    "processed_at" to payment.processedAt,
                    "loan_type" to loan?.loanType,
                    "product_name" to loan?.productName,
                    "transaction_reference" to payment.transactionReference,
                    "principal" to payment.principal,
                    "interest" to payment.interest,
                    "penalties" to payment.penalties
                ))
            }

            // Calculate Early Payoff
            get("/loans/{loanId}/early-payoff/calculate") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val loanId = call.parameters["loanId"]!!

                val loan = loanStorage.findLoanById(loanId)

                if (loan == null || loan.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Loan not found"
                    ))
                    return@get
                }

                // Calculate early payoff with discount
                val remainingBalance = loan.remainingBalance
                val earlyPayoffDiscount = remainingBalance * 0.05 // 5% discount
                val earlyPayoffAmount = remainingBalance - earlyPayoffDiscount

                call.respond(HttpStatusCode.OK, mapOf(
                    "loan_id" to loan.id,
                    "remaining_balance" to remainingBalance,
                    "early_payoff_discount" to earlyPayoffDiscount,
                    "early_payoff_amount" to earlyPayoffAmount,
                    "savings" to earlyPayoffDiscount,
                    "message" to "Pay off early and save ${earlyPayoffDiscount}"
                ))
            }
        }
    }
}

