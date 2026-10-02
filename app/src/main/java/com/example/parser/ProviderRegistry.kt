package com.example.parser

import com.example.model.FinancialProvider
import com.example.model.ParsedTransaction
import com.example.model.TransactionEntity
import java.security.MessageDigest
import java.util.UUID

data class ProviderConfig(
    val provider: FinancialProvider,
    val isEnabled: Boolean = true,
    val parser: BankSmsParser,
    val sampleTemplates: List<String>
)

object ProviderRegistry {
    private val providers = mutableListOf<ProviderConfig>()

    init {
        // Register default parsers
        register(
            FinancialProvider.TELEBIRR,
            TelebirrParser(),
            listOf(
                "Dear customer, you have received ETB 1,500.00 from 251911223344 (Abebe Bikila) on 02/10/2026 14:15:20. Your transaction number is CR123456789. Your current balance is ETB 3,450.50.",
                "You have transferred ETB 350.00 to 251922334455 (Tigist Alemu) with transaction number TR88776655 on 02/10/2026 11:05. Your current balance is ETB 3,100.50. Service fee is ETB 2.00.",
                "Payment of ETB 450.00 to merchant Sheger Supermarket (Code: 10928) was successful. Transaction number is PM998877. Your current balance is ETB 2,650.50.",
                "Dear customer, you have withdrawn ETB 500.00 at Telebirr Agent 0911002233 on 02/10/2026. Txn: CW123123. Current balance is ETB 2,150.50.",
                "You have purchased airtime of ETB 100.00 for 0911223344. Txn: AT9988. Balance is ETB 2,050.50."
            )
        )
        register(
            FinancialProvider.CBE,
            CbeParser(),
            listOf(
                "Dear Customer, Your A/C 1000123456789 has been credited with ETB 4,500.00 on 02-10-2026 09:30:15 by CBE Birr ref FT26275XYZ12. Your current balance is ETB 18,200.25.",
                "Dear Customer, Your A/C 1000123456789 has been debited with ETB 2,000.00 on 02-10-2026 at ATM Bole Branch. Available Bal: ETB 16,200.25. Ref: DB992211.",
                "Dear Customer, You have transferred ETB 800.00 from A/C 1000123456789 to 1000987654321 on 02-10-2026. Ref: TR334455. Current balance: ETB 15,400.25."
            )
        )
        register(
            FinancialProvider.AWASH_BANK,
            AwashBankParser(),
            listOf(
                "Dear Customer, your Account 01320123456700 has been credited with ETB 3,000.00 on 02/10/2026. Current balance ETB 7,500.00. Ref: AW908123.",
                "Dear Customer, your Account 01320123456700 has been debited with ETB 650.00 for POS payment at Kaldi Coffee. Current balance ETB 6,850.00. Ref: AW908124."
            )
        )
        register(
            FinancialProvider.DASHEN_BANK,
            DashenBankParser(),
            listOf(
                "Dear Customer, your account 1234567890 has been credited with ETB 2,200.00 via Amole. Current Bal: ETB 6,100.00. Ref: AM9911."
            )
        )
        register(
            FinancialProvider.BANK_OF_ABYSSINIA,
            BoAParser(),
            listOf(
                "Dear customer, you have received ETB 1,800.00 into account 4567890123 on 02/10/2026. Available balance: ETB 5,400.00. Ref: BOA7722."
            )
        )
    }

    private val genericFallback = GenericBankParser()

    fun register(provider: FinancialProvider, parser: BankSmsParser, samples: List<String>) {
        providers.removeAll { it.provider == provider }
        providers.add(ProviderConfig(provider, isEnabled = true, parser = parser, sampleTemplates = samples))
    }

    fun getAllConfigs(): List<ProviderConfig> = providers.toList()

    fun toggleProvider(provider: FinancialProvider, enabled: Boolean) {
        val idx = providers.indexOfFirst { it.provider == provider }
        if (idx >= 0) {
            providers[idx] = providers[idx].copy(isEnabled = enabled)
        }
    }

    /**
     * Executes Provider Detection -> Transaction Parser -> Fallback
     */
    fun processMessage(sender: String, messageText: String, timestampMillis: Long): ParsedTransaction? {
        // 1. Try matching enabled specific providers
        for (config in providers) {
            if (!config.isEnabled) continue
            if (config.parser.canParse(sender, messageText)) {
                val parsed = config.parser.parse(sender, messageText, timestampMillis)
                if (parsed != null && parsed.amount > 0) {
                    return parsed
                }
            }
        }

        // 2. Fallback to generic parser
        return genericFallback.parse(sender, messageText, timestampMillis)
    }

    /**
     * Computes unique message fingerprint (SHA-256) to prevent duplicates.
     */
    fun computeFingerprint(provider: String, refNumber: String?, rawMessage: String): String {
        val input = if (!refNumber.isNullOrBlank()) {
            "$provider:$refNumber"
        } else {
            // Strip timestamp and spaces to catch repeated SMS
            rawMessage.replace(Regex("""\s+"""), " ").trim().lowercase()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun toEntity(parsed: ParsedTransaction, userId: String = "local_user", source: String = "SMS"): TransactionEntity {
        val fingerprint = computeFingerprint(parsed.provider.displayName, parsed.referenceNumber, parsed.rawMessage)
        val id = UUID.randomUUID().toString()

        return TransactionEntity(
            id = id,
            userId = userId,
            provider = parsed.provider.displayName,
            account = parsed.account,
            transactionType = parsed.transactionType.name,
            category = parsed.category,
            amount = parsed.amount,
            currency = parsed.currency,
            sender = parsed.sender,
            receiver = parsed.receiver,
            referenceNumber = parsed.referenceNumber,
            description = parsed.description,
            transactionDate = parsed.transactionDate,
            transactionTime = parsed.transactionTime,
            balanceAfterTransaction = parsed.balanceAfterTransaction,
            rawMessage = parsed.rawMessage,
            source = source,
            isVerified = parsed.isConfidenceHigh,
            fingerprint = fingerprint,
            createdAt = System.currentTimeMillis()
        )
    }
}
