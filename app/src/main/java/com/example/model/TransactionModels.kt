package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType(val displayName: String, val isCredit: Boolean) {
    DEPOSIT("Deposit", true),
    TRANSFER_IN("Received Transfer", true),
    WITHDRAWAL("Cash Withdrawal", false),
    TRANSFER_OUT("Sent Transfer", false),
    PAYMENT("Merchant / Bill Payment", false),
    FEE("Service Fee / Charge", false),
    COMMISSION("Commission", true),
    REVERSAL("Reversal / Refund", true),
    FAILED("Failed Transaction", false),
    OTHER("Other", false);

    companion object {
        fun fromString(value: String): TransactionType {
            return values().firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}

enum class FinancialProvider(val id: String, val displayName: String, val senderKeywords: List<String>, val defaultColorHex: Long) {
    TELEBIRR("telebirr", "Telebirr", listOf("127", "telebirr", "ethio telecom"), 0xFF00ADEF),
    CBE("cbe", "Commercial Bank of Ethiopia (CBE)", listOf("cbe", "cbe birr", "cbebirr", "commercial bank of ethiopia"), 0xFF7A2082),
    AWASH_BANK("awash", "Awash Bank", listOf("awash", "awashbank", "awash bank"), 0xFFFF6B00),
    DASHEN_BANK("dashen", "Dashen Bank / Amole", listOf("dashen", "amole", "dashenbank"), 0xFF005696),
    BANK_OF_ABYSSINIA("boa", "Bank of Abyssinia (BoA)", listOf("boa", "abyssinia", "bankofabyssinia"), 0xFFEC671E),
    OTHER("other", "Other Bank / Payment", listOf(), 0xFF64748B);

    companion object {
        fun matchFromSenderOrText(sender: String, messageText: String): FinancialProvider {
            val lowerSender = sender.lowercase().trim()
            val lowerText = messageText.lowercase()

            for (provider in values()) {
                if (provider == OTHER) continue
                for (keyword in provider.senderKeywords) {
                    if (lowerSender.contains(keyword) || lowerText.contains(keyword)) {
                        return provider
                    }
                }
            }
            return OTHER
        }
    }
}

object TransactionCategory {
    const val SALARY_INCOME = "Salary & Income"
    const val TRANSFER = "Transfer"
    const val SHOPPING = "Shopping & Retail"
    const val FOOD_DINING = "Food & Dining"
    const val UTILITIES = "Utilities & Bills"
    const val AIRTIME = "Airtime & Data"
    const val CASH_OUT = "Cash Out / ATM"
    const val BANK_FEE = "Bank Charges & Fees"
    const val BUSINESS = "Business / Merchant"
    const val GENERAL = "General"

    val allCategories = listOf(
        SALARY_INCOME,
        TRANSFER,
        SHOPPING,
        FOOD_DINING,
        UTILITIES,
        AIRTIME,
        CASH_OUT,
        BANK_FEE,
        BUSINESS,
        GENERAL
    )

    fun deduceCategory(type: TransactionType, description: String, receiver: String?): String {
        val lower = "$description ${receiver ?: ""}".lowercase()
        return when {
            type == TransactionType.FEE -> BANK_FEE
            lower.contains("airtime") || lower.contains("package") || lower.contains("recharge") -> AIRTIME
            lower.contains("water") || lower.contains("electric") || lower.contains("ethiopian electric") || lower.contains("dstv") || lower.contains("utility") -> UTILITIES
            lower.contains("supermarket") || lower.contains("mall") || lower.contains("mart") || lower.contains("store") || lower.contains("boutique") -> SHOPPING
            lower.contains("restaurant") || lower.contains("cafe") || lower.contains("hotel") || lower.contains("burger") || lower.contains("pizza") -> FOOD_DINING
            type == TransactionType.WITHDRAWAL || lower.contains("atm") || lower.contains("cash out") -> CASH_OUT
            type == TransactionType.DEPOSIT && (lower.contains("salary") || lower.contains("allowance") || lower.contains("payroll")) -> SALARY_INCOME
            type == TransactionType.TRANSFER_IN || type == TransactionType.TRANSFER_OUT -> TRANSFER
            type == TransactionType.PAYMENT -> BUSINESS
            else -> GENERAL
        }
    }
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["referenceNumber"]),
        Index(value = ["provider"]),
        Index(value = ["transactionDate"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String = "local_user",
    val provider: String,
    val account: String,
    val transactionType: String,
    val category: String,
    val amount: Double,
    val currency: String = "ETB",
    val sender: String? = null,
    val receiver: String? = null,
    val referenceNumber: String? = null,
    val description: String? = null,
    val transactionDate: String,
    val transactionTime: String,
    val balanceAfterTransaction: Double? = null,
    val rawMessage: String? = null,
    val source: String = "SMS", // SMS, MANUAL, SIMULATOR
    val isVerified: Boolean = true,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Firestore synthetic no-arg constructor support
    constructor() : this(
        id = "",
        userId = "",
        provider = "",
        account = "",
        transactionType = TransactionType.OTHER.name,
        category = TransactionCategory.GENERAL,
        amount = 0.0,
        currency = "ETB",
        sender = null,
        receiver = null,
        referenceNumber = null,
        description = null,
        transactionDate = "",
        transactionTime = "",
        balanceAfterTransaction = null,
        rawMessage = null,
        source = "SMS",
        isVerified = true,
        fingerprint = "",
        createdAt = System.currentTimeMillis()
    )
}

data class ParsedTransaction(
    val provider: FinancialProvider,
    val account: String,
    val transactionType: TransactionType,
    val category: String,
    val amount: Double,
    val currency: String = "ETB",
    val sender: String? = null,
    val receiver: String? = null,
    val referenceNumber: String? = null,
    val description: String? = null,
    val transactionDate: String,
    val transactionTime: String,
    val balanceAfterTransaction: Double? = null,
    val rawMessage: String,
    val isConfidenceHigh: Boolean = true
)

data class DashboardSummary(
    val totalBalance: Double = 0.0,
    val totalDeposits: Double = 0.0,
    val totalWithdrawals: Double = 0.0,
    val totalTransfers: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalFees: Double = 0.0,
    val netCashFlow: Double = 0.0,
    val countToday: Int = 0,
    val countThisWeek: Int = 0,
    val countThisMonth: Int = 0,
    val providerTotals: Map<String, Double> = emptyMap(),
    val categoryTotals: Map<String, Double> = emptyMap()
)
