package com.example.parser

import com.example.model.FinancialProvider
import com.example.model.ParsedTransaction
import com.example.model.TransactionCategory
import com.example.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

interface BankSmsParser {
    val provider: FinancialProvider
    val parserId: String
    val version: String
    fun canParse(sender: String, message: String): Boolean
    fun parse(sender: String, message: String, timestampMillis: Long): ParsedTransaction?
}

// -------------------------------------------------------------
// Helper utility for regex extraction and number parsing
// -------------------------------------------------------------
object ParserUtils {
    fun parseAmount(amountStr: String): Double {
        return amountStr.replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }

    fun formatDate(timestampMillis: Long): Pair<String, String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        val date = Date(timestampMillis)
        return Pair(dateFormat.format(date), timeFormat.format(date))
    }

    fun extractWithRegex(text: String, regex: String, groupIndex: Int = 1): String? {
        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(groupIndex)?.trim() else null
    }

    fun extractReferenceNumber(text: String): String? {
        val patterns = listOf(
            """(?:txn(?:\s*no|\s*id)?|transaction\s*(?:no|number|id)|ref(?:\s*no|\s*id)?|reference(?:\s*no|\s*number)?)\s*(?:is)?\s*[:#\-]?\s*([A-Za-z0-9_\-]+)""",
            """\b(CR[0-9A-Z]{6,14}|TR[0-9A-Z]{6,14}|FT[0-9A-Z]{8,16}|PM[0-9A-Z]{6,14}|CW[0-9A-Z]{6,14})\b"""
        )
        for (p in patterns) {
            val res = extractWithRegex(text, p, 1)
            if (!res.isNullOrBlank() && !res.equals("is", ignoreCase = true)) return res
        }
        return null
    }

    fun extractBalance(text: String): Double? {
        val patterns = listOf(
            """(?:current\s*balance|available\s*bal(?:ance)?|balance|bal)\s*(?:is)?\s*[:=]?\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:balance|remaining)"""
        )
        for (p in patterns) {
            val res = extractWithRegex(text, p, 1)
            if (res != null) {
                val d = res.replace(",", "").toDoubleOrNull()
                if (d != null) return d
            }
        }
        return null
    }
}

// -------------------------------------------------------------
// Telebirr Parser (Ethio Telecom Mobile Money)
// -------------------------------------------------------------
class TelebirrParser : BankSmsParser {
    override val provider: FinancialProvider = FinancialProvider.TELEBIRR
    override val parserId: String = "telebirr_v2"
    override val version: String = "2.1.0"

    override fun canParse(sender: String, message: String): Boolean {
        val s = sender.lowercase()
        val m = message.lowercase()
        return s.contains("127") || s.contains("telebirr") || m.contains("telebirr")
    }

    override fun parse(sender: String, message: String, timestampMillis: Long): ParsedTransaction? {
        val (defaultDate, defaultTime) = ParserUtils.formatDate(timestampMillis)
        val lower = message.lowercase()

        // 1. Check for failure
        if (lower.contains("failed") || lower.contains("could not be completed") || lower.contains("unsuccessful")) {
            val amount = ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")?.let { ParserUtils.parseAmount(it) } ?: 0.0
            return ParsedTransaction(
                provider = provider,
                account = "telebirr_wallet",
                transactionType = TransactionType.FAILED,
                category = TransactionCategory.GENERAL,
                amount = amount,
                currency = "ETB",
                referenceNumber = ParserUtils.extractReferenceNumber(message),
                description = "Failed Telebirr transaction",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = ParserUtils.extractBalance(message),
                rawMessage = message
            )
        }

        // 2. Check for Reversal
        if (lower.contains("reversed") || lower.contains("reversal")) {
            val amount = ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")?.let { ParserUtils.parseAmount(it) } ?: 0.0
            return ParsedTransaction(
                provider = provider,
                account = "telebirr_wallet",
                transactionType = TransactionType.REVERSAL,
                category = TransactionCategory.GENERAL,
                amount = amount,
                currency = "ETB",
                referenceNumber = ParserUtils.extractReferenceNumber(message),
                description = "Telebirr transaction reversal",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = ParserUtils.extractBalance(message),
                rawMessage = message
            )
        }

        // 3. Deposit / Received Transfer
        // E.g.: "Dear customer, you have received ETB 1,500.00 from 251911223344 (Abebe Bikila) on 02/10/2026 14:15:20. Your transaction number is CR123456789. Your current balance is ETB 3,450.50."
        if (lower.contains("received etb") || lower.contains("received birr") || lower.contains("credited with etb") || lower.contains("deposited etb")) {
            val amountStr = ParserUtils.extractWithRegex(message, """(?:received|deposited|credited with)\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val senderParty = ParserUtils.extractWithRegex(message, """from\s+([0-9A-Za-z\s\(\)\+]+?)(?:\s+on|\s+with|\s+your|\.|\z)""")
            val refNo = ParserUtils.extractReferenceNumber(message)
            val balance = ParserUtils.extractBalance(message)
            val amount = ParserUtils.parseAmount(amountStr)

            return ParsedTransaction(
                provider = provider,
                account = "telebirr_wallet",
                transactionType = TransactionType.TRANSFER_IN,
                category = TransactionCategory.deduceCategory(TransactionType.TRANSFER_IN, "Telebirr Received", senderParty),
                amount = amount,
                currency = "ETB",
                sender = senderParty?.trim(),
                receiver = "Me",
                referenceNumber = refNo,
                description = "Received from ${senderParty ?: "Telebirr user"}",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        // 4. Sent Transfer
        // E.g.: "You have transferred ETB 250.00 to 251922334455 (Tigist Alemu) with transaction number TR88776655. Your current balance is ETB 3,200.50."
        if (lower.contains("transferred etb") || lower.contains("sent etb") || lower.contains("transferred to")) {
            val amountStr = ParserUtils.extractWithRegex(message, """(?:transferred|sent)\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val receiverParty = ParserUtils.extractWithRegex(message, """to\s+([0-9A-Za-z\s\(\)\+]+?)(?:\s+with|\s+on|\s+your|\.|\z)""")
            val refNo = ParserUtils.extractReferenceNumber(message)
            val balance = ParserUtils.extractBalance(message)
            val amount = ParserUtils.parseAmount(amountStr)

            return ParsedTransaction(
                provider = provider,
                account = "telebirr_wallet",
                transactionType = TransactionType.TRANSFER_OUT,
                category = TransactionCategory.deduceCategory(TransactionType.TRANSFER_OUT, "Telebirr Transfer", receiverParty),
                amount = amount,
                currency = "ETB",
                sender = "Me",
                receiver = receiverParty?.trim(),
                referenceNumber = refNo,
                description = "Sent to ${receiverParty ?: "Telebirr recipient"}",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        // 5. Merchant / Bill Payment
        // E.g.: "Payment of ETB 450.00 to merchant Sheger Supermarket was successful. Transaction number is PM998877. Your current balance is ETB 2,750.50."
        if (lower.contains("payment of etb") || lower.contains("paid etb") || lower.contains("merchant") || lower.contains("bill payment")) {
            val amountStr = ParserUtils.extractWithRegex(message, """(?:payment of|paid)\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val merchant = ParserUtils.extractWithRegex(message, """(?:to merchant|to)\s+([0-9A-Za-z\s\(\)\-]+?)(?:\s+was|\s+with|\s+on|\.|\z)""")
            val refNo = ParserUtils.extractReferenceNumber(message)
            val balance = ParserUtils.extractBalance(message)
            val amount = ParserUtils.parseAmount(amountStr)

            return ParsedTransaction(
                provider = provider,
                account = "telebirr_wallet",
                transactionType = TransactionType.PAYMENT,
                category = TransactionCategory.deduceCategory(TransactionType.PAYMENT, merchant ?: "Payment", merchant),
                amount = amount,
                currency = "ETB",
                sender = "Me",
                receiver = merchant?.trim() ?: "Merchant",
                referenceNumber = refNo,
                description = "Payment to ${merchant ?: "Merchant"}",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        // 6. Cash Out / Withdrawal
        // E.g.: "Dear customer, you have withdrawn ETB 500.00 at Telebirr Agent 0911002233..."
        if (lower.contains("withdrawn etb") || lower.contains("cash out") || lower.contains("withdrew")) {
            val amountStr = ParserUtils.extractWithRegex(message, """(?:withdrawn|withdrew|cash out)\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val agent = ParserUtils.extractWithRegex(message, """at\s+(?:telebirr agent|agent)?\s*([0-9A-Za-z\s\(\)]+?)(?:\s+on|\s+with|\.|\z)""")
            val refNo = ParserUtils.extractReferenceNumber(message)
            val balance = ParserUtils.extractBalance(message)
            val amount = ParserUtils.parseAmount(amountStr)

            return ParsedTransaction(
                provider = provider,
                account = "telebirr_wallet",
                transactionType = TransactionType.WITHDRAWAL,
                category = TransactionCategory.CASH_OUT,
                amount = amount,
                currency = "ETB",
                sender = "Me",
                receiver = agent?.trim() ?: "Agent Cash-Out",
                referenceNumber = refNo,
                description = "Withdrawn at ${agent ?: "Agent"}",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        // 7. Airtime / Package
        if (lower.contains("airtime") || lower.contains("package")) {
            val amountStr = ParserUtils.extractWithRegex(message, """(?:airtime|package)\s*(?:of)?\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val recipientPhone = ParserUtils.extractWithRegex(message, """for\s+([0-9\+]+)""")
            val refNo = ParserUtils.extractReferenceNumber(message)
            val balance = ParserUtils.extractBalance(message)
            val amount = ParserUtils.parseAmount(amountStr)

            return ParsedTransaction(
                provider = provider,
                account = "telebirr_wallet",
                transactionType = TransactionType.PAYMENT,
                category = TransactionCategory.AIRTIME,
                amount = amount,
                currency = "ETB",
                sender = "Me",
                receiver = recipientPhone ?: "Ethio Telecom",
                referenceNumber = refNo,
                description = "Airtime purchase ${recipientPhone ?: ""}".trim(),
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        // Fallback: general telebirr extraction
        val amount = ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")?.let { ParserUtils.parseAmount(it) } ?: 0.0
        return ParsedTransaction(
            provider = provider,
            account = "telebirr_wallet",
            transactionType = if (lower.contains("credited") || lower.contains("received")) TransactionType.DEPOSIT else TransactionType.OTHER,
            category = TransactionCategory.GENERAL,
            amount = amount,
            currency = "ETB",
            referenceNumber = ParserUtils.extractReferenceNumber(message),
            description = "Telebirr notification",
            transactionDate = defaultDate,
            transactionTime = defaultTime,
            balanceAfterTransaction = ParserUtils.extractBalance(message),
            rawMessage = message
        )
    }
}

// -------------------------------------------------------------
// Commercial Bank of Ethiopia (CBE) Parser
// -------------------------------------------------------------
class CbeParser : BankSmsParser {
    override val provider: FinancialProvider = FinancialProvider.CBE
    override val parserId: String = "cbe_standard_v2"
    override val version: String = "2.1.0"

    override fun canParse(sender: String, message: String): Boolean {
        val s = sender.lowercase()
        val m = message.lowercase()
        return s.contains("cbe") || m.contains("cbe") || m.contains("commercial bank of ethiopia") || m.contains("cbe birr")
    }

    override fun parse(sender: String, message: String, timestampMillis: Long): ParsedTransaction? {
        val (defaultDate, defaultTime) = ParserUtils.formatDate(timestampMillis)
        val lower = message.lowercase()

        // Extract CBE account number (usually 1000... or partial)
        val account = ParserUtils.extractWithRegex(message, """(?:A/C|Account|Acc(?:\.)?)\s*[:#]?\s*([0-9*X]+)""") ?: "CBE_Account"

        val refNo = ParserUtils.extractReferenceNumber(message)
        val balance = ParserUtils.extractBalance(message)

        // 1. Credit / Deposit
        // "Dear Customer, Your A/C 1000123456789 has been credited with ETB 3,200.00 on 02-10-2026 by CBE Birr / Mobile Banking ref FT26275XYZ12. Your current balance is ETB 14,800.25."
        if (lower.contains("credited with") || lower.contains("has been credited") || lower.contains("deposited")) {
            val amountStr = ParserUtils.extractWithRegex(message, """credited(?:\s*with)?\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val senderParty = ParserUtils.extractWithRegex(message, """by\s+([0-9A-Za-z\s\/\.\-]+?)(?:\s+ref|\s+your|\.|\z)""")
            val amount = ParserUtils.parseAmount(amountStr)

            return ParsedTransaction(
                provider = provider,
                account = account,
                transactionType = TransactionType.DEPOSIT,
                category = TransactionCategory.deduceCategory(TransactionType.DEPOSIT, "CBE Deposit", senderParty),
                amount = amount,
                currency = "ETB",
                sender = senderParty?.trim() ?: "Depositor",
                receiver = "Me",
                referenceNumber = refNo,
                description = "Credited via ${senderParty?.trim() ?: "CBE transfer"}",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        // 2. Debit / Withdrawal / Transfer
        // "Dear Customer, Your A/C 1000123456789 has been debited with ETB 1,000.00 on 02-10-2026 at ATM Addis Ababa. Available Bal: ETB 13,800.25. Ref: DB992211."
        if (lower.contains("debited with") || lower.contains("has been debited") || lower.contains("withdrawn") || lower.contains("transferred")) {
            val amountStr = ParserUtils.extractWithRegex(message, """(?:debited(?:\s*with)?|withdrawn|transferred)\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val channel = ParserUtils.extractWithRegex(message, """(?:at|via|to)\s+([0-9A-Za-z\s\/\.\-]+?)(?:\s+ref|\s+available|\s+your|\.|\z)""")
            val amount = ParserUtils.parseAmount(amountStr)

            val isAtm = lower.contains("atm")
            val isTransfer = lower.contains("transfer")

            val txnType = when {
                isAtm -> TransactionType.WITHDRAWAL
                isTransfer -> TransactionType.TRANSFER_OUT
                lower.contains("fee") || lower.contains("charge") -> TransactionType.FEE
                else -> TransactionType.PAYMENT
            }

            return ParsedTransaction(
                provider = provider,
                account = account,
                transactionType = txnType,
                category = TransactionCategory.deduceCategory(txnType, channel ?: "CBE Debit", channel),
                amount = amount,
                currency = "ETB",
                sender = "Me",
                receiver = channel?.trim() ?: "CBE Payment",
                referenceNumber = refNo,
                description = "Debited via ${channel?.trim() ?: "CBE"}",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        // Fallback for CBE
        val amount = ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")?.let { ParserUtils.parseAmount(it) } ?: 0.0
        return ParsedTransaction(
            provider = provider,
            account = account,
            transactionType = TransactionType.OTHER,
            category = TransactionCategory.GENERAL,
            amount = amount,
            currency = "ETB",
            referenceNumber = refNo,
            description = "CBE notification",
            transactionDate = defaultDate,
            transactionTime = defaultTime,
            balanceAfterTransaction = balance,
            rawMessage = message
        )
    }
}

// -------------------------------------------------------------
// Awash Bank Parser
// -------------------------------------------------------------
class AwashBankParser : BankSmsParser {
    override val provider: FinancialProvider = FinancialProvider.AWASH_BANK
    override val parserId: String = "awash_v1"
    override val version: String = "1.2.0"

    override fun canParse(sender: String, message: String): Boolean {
        val s = sender.lowercase()
        val m = message.lowercase()
        return s.contains("awash") || m.contains("awash") || m.contains("awash bank")
    }

    override fun parse(sender: String, message: String, timestampMillis: Long): ParsedTransaction? {
        val (defaultDate, defaultTime) = ParserUtils.formatDate(timestampMillis)
        val lower = message.lowercase()
        val account = ParserUtils.extractWithRegex(message, """(?:Account|Acc(?:\.)?|A/C)\s*[:#]?\s*([0-9*X]+)""") ?: "Awash_Account"
        val refNo = ParserUtils.extractReferenceNumber(message)
        val balance = ParserUtils.extractBalance(message)

        if (lower.contains("credited")) {
            val amountStr = ParserUtils.extractWithRegex(message, """credited(?:\s*with)?\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val amount = ParserUtils.parseAmount(amountStr)

            return ParsedTransaction(
                provider = provider,
                account = account,
                transactionType = TransactionType.DEPOSIT,
                category = TransactionCategory.deduceCategory(TransactionType.DEPOSIT, "Awash Deposit", null),
                amount = amount,
                currency = "ETB",
                sender = "Depositor",
                receiver = "Me",
                referenceNumber = refNo,
                description = "Credited to Awash Bank",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        if (lower.contains("debited") || lower.contains("withdrawn") || lower.contains("transferred")) {
            val amountStr = ParserUtils.extractWithRegex(message, """(?:debited(?:\s*with)?|withdrawn|transferred)\s*(?:ETB|Birr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
                ?: ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
            val reason = ParserUtils.extractWithRegex(message, """for\s+([0-9A-Za-z\s\/\.\-]+?)(?:\s+ref|\s+current|\.|\z)""")
            val amount = ParserUtils.parseAmount(amountStr)

            val type = if (lower.contains("atm")) TransactionType.WITHDRAWAL else TransactionType.PAYMENT
            return ParsedTransaction(
                provider = provider,
                account = account,
                transactionType = type,
                category = TransactionCategory.deduceCategory(type, reason ?: "Debit", null),
                amount = amount,
                currency = "ETB",
                sender = "Me",
                receiver = reason?.trim() ?: "Awash Outflow",
                referenceNumber = refNo,
                description = "Debited: ${reason ?: "Transaction"}",
                transactionDate = defaultDate,
                transactionTime = defaultTime,
                balanceAfterTransaction = balance,
                rawMessage = message
            )
        }

        val amount = ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")?.let { ParserUtils.parseAmount(it) } ?: 0.0
        return ParsedTransaction(
            provider = provider,
            account = account,
            transactionType = TransactionType.OTHER,
            category = TransactionCategory.GENERAL,
            amount = amount,
            currency = "ETB",
            referenceNumber = refNo,
            description = "Awash Bank message",
            transactionDate = defaultDate,
            transactionTime = defaultTime,
            balanceAfterTransaction = balance,
            rawMessage = message
        )
    }
}

// -------------------------------------------------------------
// Dashen Bank / Amole Parser
// -------------------------------------------------------------
class DashenBankParser : BankSmsParser {
    override val provider: FinancialProvider = FinancialProvider.DASHEN_BANK
    override val parserId: String = "dashen_amole_v1"
    override val version: String = "1.0.0"

    override fun canParse(sender: String, message: String): Boolean {
        val s = sender.lowercase()
        val m = message.lowercase()
        return s.contains("dashen") || s.contains("amole") || m.contains("dashen") || m.contains("amole")
    }

    override fun parse(sender: String, message: String, timestampMillis: Long): ParsedTransaction? {
        val (defaultDate, defaultTime) = ParserUtils.formatDate(timestampMillis)
        val lower = message.lowercase()
        val account = ParserUtils.extractWithRegex(message, """(?:account|acc(?:\.)?|a/c)\s*[:#]?\s*([0-9*X]+)""") ?: "Dashen_Account"
        val refNo = ParserUtils.extractReferenceNumber(message)
        val balance = ParserUtils.extractBalance(message)

        val isCredit = lower.contains("credited") || lower.contains("received") || lower.contains("deposited")
        val amountStr = ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
        val amount = ParserUtils.parseAmount(amountStr)

        val txnType = if (isCredit) TransactionType.DEPOSIT else TransactionType.WITHDRAWAL

        return ParsedTransaction(
            provider = provider,
            account = account,
            transactionType = txnType,
            category = TransactionCategory.deduceCategory(txnType, "Dashen / Amole", null),
            amount = amount,
            currency = "ETB",
            sender = if (isCredit) "Sender" else "Me",
            receiver = if (isCredit) "Me" else "Dashen/Amole",
            referenceNumber = refNo,
            description = if (isCredit) "Credited into Dashen account" else "Debited from Dashen account",
            transactionDate = defaultDate,
            transactionTime = defaultTime,
            balanceAfterTransaction = balance,
            rawMessage = message
        )
    }
}

// -------------------------------------------------------------
// Bank of Abyssinia (BoA) Parser
// -------------------------------------------------------------
class BoAParser : BankSmsParser {
    override val provider: FinancialProvider = FinancialProvider.BANK_OF_ABYSSINIA
    override val parserId: String = "boa_apollo_v1"
    override val version: String = "1.0.0"

    override fun canParse(sender: String, message: String): Boolean {
        val s = sender.lowercase()
        val m = message.lowercase()
        return s.contains("boa") || s.contains("abyssinia") || m.contains("bank of abyssinia") || m.contains("apollo")
    }

    override fun parse(sender: String, message: String, timestampMillis: Long): ParsedTransaction? {
        val (defaultDate, defaultTime) = ParserUtils.formatDate(timestampMillis)
        val lower = message.lowercase()
        val account = ParserUtils.extractWithRegex(message, """(?:account|acc(?:\.)?|a/c)\s*[:#]?\s*([0-9*X]+)""") ?: "BoA_Account"
        val refNo = ParserUtils.extractReferenceNumber(message)
        val balance = ParserUtils.extractBalance(message)

        val isCredit = lower.contains("credited") || lower.contains("received") || lower.contains("deposited")
        val amountStr = ParserUtils.extractWithRegex(message, """(?:ETB|Birr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""") ?: "0"
        val amount = ParserUtils.parseAmount(amountStr)

        val txnType = if (isCredit) TransactionType.DEPOSIT else TransactionType.PAYMENT

        return ParsedTransaction(
            provider = provider,
            account = account,
            transactionType = txnType,
            category = TransactionCategory.deduceCategory(txnType, "Bank of Abyssinia", null),
            amount = amount,
            currency = "ETB",
            sender = if (isCredit) "Sender" else "Me",
            receiver = if (isCredit) "Me" else "Merchant/Recipient",
            referenceNumber = refNo,
            description = if (isCredit) "Credited into BoA account" else "Debited from BoA account",
            transactionDate = defaultDate,
            transactionTime = defaultTime,
            balanceAfterTransaction = balance,
            rawMessage = message
        )
    }
}

// -------------------------------------------------------------
// Generic Fallback Parser (Robust universal parser for any bank)
// -------------------------------------------------------------
class GenericBankParser : BankSmsParser {
    override val provider: FinancialProvider = FinancialProvider.OTHER
    override val parserId: String = "generic_bank_fallback_v1"
    override val version: String = "1.0.0"

    override fun canParse(sender: String, message: String): Boolean = true // Always matches as last resort

    override fun parse(sender: String, message: String, timestampMillis: Long): ParsedTransaction? {
        val (defaultDate, defaultTime) = ParserUtils.formatDate(timestampMillis)
        val lower = message.lowercase()

        // Extract amount: e.g. ETB 500, Birr 250.50, $100.00, or raw number with credit/debit
        val amountStr = ParserUtils.extractWithRegex(message, """(?:ETB|Birr|USD|\$)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
            ?: ParserUtils.extractWithRegex(message, """([0-9,]+(?:\.[0-9]{2}))\s*(?:ETB|Birr)""")
            ?: ParserUtils.extractWithRegex(message, """(?:amount(?:\s*is)?|for)\s*[:=]?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")

        if (amountStr == null) return null // No recognizable financial amount

        val amount = ParserUtils.parseAmount(amountStr)
        val balance = ParserUtils.extractBalance(message)
        val refNo = ParserUtils.extractReferenceNumber(message)
        val detectedProvider = FinancialProvider.matchFromSenderOrText(sender, message)

        val txnType = when {
            lower.contains("fee") || lower.contains("commission") || lower.contains("charge") -> TransactionType.FEE
            lower.contains("withdrawn") || lower.contains("cash out") || lower.contains("atm") -> TransactionType.WITHDRAWAL
            lower.contains("transferred to") || lower.contains("sent") -> TransactionType.TRANSFER_OUT
            lower.contains("received") || lower.contains("credited") || lower.contains("deposited") -> TransactionType.DEPOSIT
            lower.contains("payment") || lower.contains("paid") || lower.contains("purchased") -> TransactionType.PAYMENT
            lower.contains("revers") -> TransactionType.REVERSAL
            lower.contains("failed") -> TransactionType.FAILED
            else -> TransactionType.OTHER
        }

        return ParsedTransaction(
            provider = detectedProvider,
            account = if (sender.isNotBlank()) sender else "Main_Account",
            transactionType = txnType,
            category = TransactionCategory.deduceCategory(txnType, "Transaction", null),
            amount = amount,
            currency = if (lower.contains("usd") || lower.contains("$")) "USD" else "ETB",
            sender = if (txnType.isCredit) sender else "Me",
            receiver = if (!txnType.isCredit) sender else "Me",
            referenceNumber = refNo,
            description = "Transaction via ${detectedProvider.displayName}",
            transactionDate = defaultDate,
            transactionTime = defaultTime,
            balanceAfterTransaction = balance,
            rawMessage = message,
            isConfidenceHigh = false
        )
    }
}
