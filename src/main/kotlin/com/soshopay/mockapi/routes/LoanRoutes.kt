package com.soshopay.mockapi.routes

import com.soshopay.mockapi.models.CashLoanApplicationRequest
import com.soshopay.mockapi.models.CashLoanCalculationRequest
import com.soshopay.mockapi.models.PayGoApplicationRequest
import com.soshopay.mockapi.models.PayGoCalculationRequest
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun Route.loanRoutes(
    loanStorage: LoanStorage,
    clientStorage: ClientStorage,
    loanWorkflow: LoanApplicationWorkflow
) {
    route("/api/loans") {

        authenticate("auth-jwt") {

            // Get Cash Loan Form Data
            get("/cash/form-data") {
                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "purposes" to listOf(
                            "PERSONAL", "BUSINESS", "EDUCATION",
                            "MEDICAL", "HOME_IMPROVEMENT", "OTHER"
                        ),
                        "repayment_periods" to listOf(
                            mapOf("value" to "3_MONTHS", "label" to "3 Months"),
                            mapOf("value" to "6_MONTHS", "label" to "6 Months"),
                            mapOf("value" to "12_MONTHS", "label" to "12 Months"),
                            mapOf("value" to "18_MONTHS", "label" to "18 Months"),
                            mapOf("value" to "24_MONTHS", "label" to "24 Months")
                        ),
                        "min_amount" to 1000.0,
                        "max_amount" to 100000.0,
                        "industries" to listOf(
                            "AGRICULTURE", "MINING", "MANUFACTURING",
                            "CONSTRUCTION", "RETAIL", "HOSPITALITY",
                            "TRANSPORT", "FINANCE", "TECHNOLOGY", "OTHER"
                        )
                    )
                )
            }

            // Calculate Cash Loan Terms
            post("/cash/calculate") {
                val request = call.receive<CashLoanCalculationRequest>()

                val interestRate = when {
                    request.loanAmount < 10000 -> 0.15
                    request.loanAmount < 50000 -> 0.12
                    else -> 0.10
                }

                val totalAmount = request.loanAmount * (1 + interestRate)
                val months = when (request.repaymentPeriod) {
                    "3_MONTHS" -> 3
                    "6_MONTHS" -> 6
                    "12_MONTHS" -> 12
                    "18_MONTHS" -> 18
                    "24_MONTHS" -> 24
                    else -> 12
                }

                val monthlyPayment = totalAmount / months

                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "loan_amount" to request.loanAmount,
                        "interest_rate" to interestRate,
                        "total_amount" to totalAmount,
                        "repayment_period" to request.repaymentPeriod,
                        "monthly_payment" to monthlyPayment,
                        "total_payments" to months,
                        "total_interest" to (totalAmount - request.loanAmount)
                    )
                )
            }

            // Submit Cash Loan Application
            post("/cash/apply") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<CashLoanApplicationRequest>()

                // Get client
                val client = clientStorage.findById(userId)
                if (client == null) {
                    call.respond(
                        HttpStatusCode.NotFound, mapOf(
                            "message" to "Client not found"
                        )
                    )
                    return@post
                }

                // Check eligibility
                if (!client.canApplyForLoan) {
                    call.respond(
                        HttpStatusCode.BadRequest, mapOf(
                            "message" to "Profile incomplete or not verified. Please complete your profile and upload required documents."
                        )
                    )
                    return@post
                }

                // Create application
                val application = loanStorage.createApplication(
                    userId = userId,
                    loanType = "CASH",
                    loanAmount = request.loanAmount,
                    repaymentPeriod = request.repaymentPeriod,
                    loanPurpose = request.loanPurpose,
                    productName = null
                )

                // Process application asynchronously
                launch {
                    val result = loanWorkflow.processApplication(
                        applicationId = application.id,
                        loanType = "CASH",
                        amount = request.loanAmount,
                        clientVerificationStatus = client.verificationStatus
                    )

                    // Update application status
                    loanStorage.updateApplicationStatus(
                        applicationId = application.id,
                        status = result.status,
                        rejectionReason = result.rejectionReason
                    )

                    // If approved, create loan and disburse
                    if (result.status == LoanApplicationStatus.APPROVED) {
                        val loan = loanStorage.createLoanFromApplication(application)
                        println("✅ Loan ${loan.id} disbursed for application ${application.id}")
                    }
                }

                call.respond(
                    HttpStatusCode.Created, mapOf(
                        "application_id" to application.id,
                        "status" to "SUBMITTED",
                        "message" to "Your loan application has been submitted successfully",
                        "estimated_review_time" to "2-5 minutes"
                    )
                )
            }

            // Get PayGo Categories
            get("/paygo/categories") {
                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "categories" to listOf(
                            "SOLAR_POWER",
                            "SMARTPHONES",
                            "LAPTOPS",
                            "HOME_APPLIANCES",
                            "FARMING_EQUIPMENT",
                            "MOTORCYCLES",
                            "BICYCLES"
                        )
                    )
                )
            }

            // Get Category Products
            get("/paygo/categories/{categoryId}/products") {
                val categoryId = call.parameters["categoryId"]!!

                val products = when (categoryId) {
                    "SOLAR_POWER" -> listOf(
                        mapOf(
                            "id" to "SOLAR_001",
                            "name" to "Solar Home System 50W",
                            "category" to "SOLAR_POWER",
                            "price" to 45000.0,
                            "description" to "Complete solar kit with 2 bulbs, phone charging",
                            "specifications" to mapOf(
                                "power" to "50W",
                                "battery" to "20Ah",
                                "warranty" to "2 years"
                            )
                        ),
                        mapOf(
                            "id" to "SOLAR_002",
                            "name" to "Solar Home System 100W",
                            "category" to "SOLAR_POWER",
                            "price" to 85000.0,
                            "description" to "Advanced solar kit with 4 bulbs, TV, phone charging",
                            "specifications" to mapOf(
                                "power" to "100W",
                                "battery" to "40Ah",
                                "warranty" to "3 years"
                            )
                        )
                    )

                    "SMARTPHONES" -> listOf(
                        mapOf(
                            "id" to "PHONE_001",
                            "name" to "Tecno Spark 10",
                            "category" to "SMARTPHONES",
                            "price" to 15000.0,
                            "description" to "Android smartphone with 4GB RAM",
                            "specifications" to mapOf(
                                "ram" to "4GB",
                                "storage" to "64GB",
                                "warranty" to "1 year"
                            )
                        ),
                        mapOf(
                            "id" to "PHONE_002",
                            "name" to "Samsung Galaxy A14",
                            "category" to "SMARTPHONES",
                            "price" to 28000.0,
                            "description" to "Android smartphone with 6GB RAM",
                            "specifications" to mapOf(
                                "ram" to "6GB",
                                "storage" to "128GB",
                                "warranty" to "1 year"
                            )
                        )
                    )

                    else -> listOf(
                        mapOf(
                            "id" to "PROD_001",
                            "name" to "Sample Product",
                            "category" to categoryId,
                            "price" to 50000.0,
                            "description" to "Sample product description"
                        )
                    )
                }

                call.respond(HttpStatusCode.OK, mapOf("products" to products))
            }

            // Calculate PayGo Terms
            post("/paygo/calculate") {
                val request = call.receive<PayGoCalculationRequest>()

                val interestRate = 0.18 // 18% for PayGo
                val totalAmount = request.productPrice * (1 + interestRate)

                val months = when (request.repaymentPeriod) {
                    "6_MONTHS" -> 6
                    "12_MONTHS" -> 12
                    "18_MONTHS" -> 18
                    "24_MONTHS" -> 24
                    else -> 12
                }

                val monthlyPayment = totalAmount / months

                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "product_price" to request.productPrice,
                        "interest_rate" to interestRate,
                        "total_amount" to totalAmount,
                        "repayment_period" to request.repaymentPeriod,
                        "monthly_payment" to monthlyPayment,
                        "total_payments" to months,
                        "total_interest" to (totalAmount - request.productPrice),
                        "down_payment" to (request.productPrice * 0.1) // 10% down payment
                    )
                )
            }

            // Submit PayGo Application
            post("/paygo/apply") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<PayGoApplicationRequest>()

                val client = clientStorage.findById(userId)
                if (client == null || !client.canApplyForLoan) {
                    call.respond(
                        HttpStatusCode.BadRequest, mapOf(
                            "message" to "Profile incomplete or not verified"
                        )
                    )
                    return@post
                }

                val application = loanStorage.createApplication(
                    userId = userId,
                    loanType = "PAYGO",
                    loanAmount = request.productPrice,
                    repaymentPeriod = request.repaymentPeriod,
                    loanPurpose = "PRODUCT_PURCHASE",
                    productName = request.productName
                )

                // Process application asynchronously
                launch {
                    val result = loanWorkflow.processApplication(
                        applicationId = application.id,
                        loanType = "PAYGO",
                        amount = request.productPrice,
                        clientVerificationStatus = client.verificationStatus
                    )

                    loanStorage.updateApplicationStatus(
                        applicationId = application.id,
                        status = result.status,
                        rejectionReason = result.rejectionReason
                    )

                    if (result.status == LoanApplicationStatus.APPROVED) {
                        loanStorage.createLoanFromApplication(application)
                    }
                }

                call.respond(
                    HttpStatusCode.Created, mapOf(
                        "application_id" to application.id,
                        "status" to "SUBMITTED",
                        "message" to "Your PayGo application has been submitted successfully",
                        "estimated_review_time" to "2-5 minutes"
                    )
                )
            }

            // Get Loan History
            get("/history") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val filter = call.request.queryParameters["filter"] ?: "all"
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val loans = loanStorage.findLoansByUserId(userId)

                val filtered = when (filter) {
                    "active" -> loans.filter { it.status == "ACTIVE" }
                    "completed" -> loans.filter { it.status == "COMPLETED" }
                    "defaulted" -> loans.filter { it.status == "DEFAULTED" }
                    else -> loans
                }

                val total = filtered.size
                val startIndex = (page - 1) * limit
                val endIndex = minOf(startIndex + limit, total)
                val paginatedLoans = if (startIndex < total) filtered.subList(startIndex, endIndex) else emptyList()

                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "loans" to paginatedLoans,
                        "pagination" to mapOf(
                            "page" to page,
                            "limit" to limit,
                            "total" to total,
                            "total_pages" to ((total + limit - 1) / limit)
                        )
                    )
                )
            }

            // Get Loan Details
            get("/{loanId}/details") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val loanId = call.parameters["loanId"]!!

                val loan = loanStorage.findLoanById(loanId)

                if (loan == null || loan.userId != userId) {
                    call.respond(
                        HttpStatusCode.NotFound, mapOf(
                            "message" to "Loan not found"
                        )
                    )
                    return@get
                }

                // Generate payment schedule
                val schedule = generatePaymentSchedule(loan)

                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "loan" to loan,
                        "payment_schedule" to schedule
                    )
                )
            }

            // Get Current Active Loans
            get("/current") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val loans = loanStorage.findLoansByUserId(userId)
                    .filter { it.status == "ACTIVE" }

                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "loans" to loans
                    )
                )
            }

            // Download Loan Agreement (Mock PDF)
            get("/{loanId}/agreement") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val loanId = call.parameters["loanId"]!!

                val loan = loanStorage.findLoanById(loanId)

                if (loan == null || loan.userId != userId) {
                    call.respond(
                        HttpStatusCode.NotFound, mapOf(
                            "message" to "Loan not found"
                        )
                    )
                    return@get
                }

                // Mock PDF content
                val pdfContent =
                    "LOAN AGREEMENT - ${loan.id}\n\nAmount: ${loan.originalAmount}\nTerms: ${loan.repaymentPeriod}".toByteArray()

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"loan_agreement_${loan.id}.pdf\""
                )
                call.respondBytes(pdfContent, ContentType.Application.Pdf)
            }

            // Withdraw Application
            post("/applications/{applicationId}/withdraw") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val applicationId = call.parameters["applicationId"]!!

                val application = loanStorage.findApplicationById(applicationId)

                if (application == null || application.userId != userId) {
                    call.respond(
                        HttpStatusCode.NotFound, mapOf(
                            "message" to "Application not found"
                        )
                    )
                    return@post
                }

                if (application.status != "SUBMITTED" && application.status != "UNDER_REVIEW") {
                    call.respond(
                        HttpStatusCode.BadRequest, mapOf(
                            "message" to "Application cannot be withdrawn at this stage"
                        )
                    )
                    return@post
                }

                loanStorage.updateApplicationStatus(
                    applicationId = applicationId,
                    status = LoanApplicationStatus.CANCELLED
                )

                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "message" to "Application withdrawn successfully",
                        "success" to true
                    )
                )
            }
        }
    }
}

// Helper function to generate payment schedule

private fun generatePaymentSchedule(loan: Loan): List<Map<String, Any?>> {
    val schedule = mutableListOf<Map<String, Any?>>()
    val monthlyPayment = loan.nextPaymentAmount ?: (loan.totalAmount / loan.totalPayments)
    for (i in 1..loan.totalPayments) {
        val dueDate = loan.disbursementDate + (i * 30L * 24 * 60 * 60 * 1000)
        val isPaid = i <= loan.paymentsCompleted

        schedule.add(
            mapOf(
                "payment_number" to i,
                "due_date" to dueDate,
                "amount" to monthlyPayment,
                "principal" to (monthlyPayment * 0.85),
                "interest" to (monthlyPayment * 0.15),
                "status" to if (isPaid) "PAID" else "PENDING",
                "paid_date" to if (isPaid) (dueDate - (5L * 24 * 60 * 60 * 1000)) else null
            )
        )
    }

    return schedule
}
