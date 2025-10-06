package com.soshopay.mockapi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class CashLoanCalculationRequest(
    @SerialName("loan_amount") val loanAmount: Double,
    @SerialName("repayment_period") val repaymentPeriod: String
)
@kotlinx.serialization.Serializable
data class CashLoanApplicationRequest(
    @SerialName("loan_amount") val loanAmount: Double,
    @SerialName("repayment_period") val repaymentPeriod: String,
    @SerialName("loan_purpose") val loanPurpose: String,
    @SerialName("monthly_income") val monthlyIncome: Double,
    @SerialName("employer_industry") val employerIndustry: String,
    @SerialName("collateral_type") val collateralType: String?,
    @SerialName("collateral_value") val collateralValue: Double?
)
@kotlinx.serialization.Serializable
data class PayGoCalculationRequest(
    @SerialName("product_price") val productPrice: Double,
    @SerialName("repayment_period") val repaymentPeriod: String
)
@Serializable
data class PayGoApplicationRequest(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("product_price") val productPrice: Double,
    @SerialName("repayment_period") val repaymentPeriod: String,
    @SerialName("delivery_address") val deliveryAddress: String
)

