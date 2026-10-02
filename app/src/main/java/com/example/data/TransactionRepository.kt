package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.DashboardSummary
import com.example.model.FinancialProvider
import com.example.model.TransactionCategory
import com.example.model.TransactionEntity
import com.example.model.TransactionType
import com.example.parser.ProviderRegistry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ProcessingResult {
    data class Success(val transaction: TransactionEntity, val isDuplicate: Boolean) : ProcessingResult()
    data class Failure(val reason: String) : ProcessingResult()
}

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val firestoreDb: FirebaseFirestore? = null
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions().flowOn(Dispatchers.IO)

    private fun getCurrentUserId(): String? {
        return try {
            Firebase.auth.currentUser?.uid
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun processIncomingMessage(
        sender: String,
        message: String,
        timestampMillis: Long = System.currentTimeMillis(),
        source: String = "SMS"
    ): ProcessingResult = withContext(Dispatchers.IO) {
        val parsed = ProviderRegistry.processMessage(sender, message, timestampMillis)
            ?: return@withContext ProcessingResult.Failure("Message could not be recognized as a financial transaction from supported banks.")

        val userId = getCurrentUserId() ?: "local_user"
        val entity = ProviderRegistry.toEntity(parsed, userId = userId, source = source)

        // 1. Check for duplicates using reference number (if present)
        if (!entity.referenceNumber.isNullOrBlank()) {
            val existingByRef = transactionDao.findByReferenceNumber(entity.referenceNumber)
            if (existingByRef != null) {
                return@withContext ProcessingResult.Success(existingByRef, isDuplicate = true)
            }
        }

        // 2. Check for duplicate by fingerprint
        val existingByFingerprint = transactionDao.findByFingerprint(entity.fingerprint)
        if (existingByFingerprint != null) {
            return@withContext ProcessingResult.Success(existingByFingerprint, isDuplicate = true)
        }

        // 3. Insert into Room local database
        transactionDao.insertTransaction(entity)

        // 4. Sync to Firestore if authenticated
        syncToFirestore(entity)

        ProcessingResult.Success(entity, isDuplicate = false)
    }

    suspend fun insertTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: "local_user"
        val toSave = transaction.copy(userId = userId)
        transactionDao.insertTransaction(toSave)
        syncToFirestore(toSave)
    }

    suspend fun deleteTransaction(id: String) = withContext(Dispatchers.IO) {
        transactionDao.deleteById(id)
        val uid = getCurrentUserId()
        if (uid != null && firestoreDb != null) {
            try {
                firestoreDb.collection("users").document(uid).collection("transactions").document(id).delete()
            } catch (e: Exception) {
                Log.e("TransactionRepo", "Failed to delete from Firestore: ${e.message}")
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        transactionDao.clearAll()
    }

    private fun syncToFirestore(entity: TransactionEntity) {
        val uid = getCurrentUserId() ?: return
        val db = firestoreDb ?: return

        val payload = hashMapOf<String, Any?>(
            "id" to entity.id,
            "userId" to uid,
            "provider" to entity.provider,
            "account" to entity.account,
            "transactionType" to entity.transactionType,
            "category" to entity.category,
            "amount" to entity.amount,
            "currency" to entity.currency,
            "sender" to entity.sender,
            "receiver" to entity.receiver,
            "referenceNumber" to entity.referenceNumber,
            "description" to entity.description,
            "transactionDate" to entity.transactionDate,
            "transactionTime" to entity.transactionTime,
            "balanceAfterTransaction" to entity.balanceAfterTransaction,
            "rawMessage" to entity.rawMessage,
            "source" to entity.source,
            "isVerified" to entity.isVerified,
            "fingerprint" to entity.fingerprint,
            "createdAt" to com.google.firebase.Timestamp(Date(entity.createdAt))
        )

        db.collection("users")
            .document(uid)
            .collection("transactions")
            .document(entity.id)
            .set(payload, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.w("TransactionRepo", "Firestore sync failed: ${e.message}")
            }
    }

    fun calculateSummary(transactions: List<TransactionEntity>): DashboardSummary {
        var totalDeposits = 0.0
        var totalWithdrawals = 0.0
        var totalTransfers = 0.0
        var totalExpenses = 0.0
        var totalIncome = 0.0
        var totalFees = 0.0

        val providerMap = mutableMapOf<String, Double>()
        val categoryMap = mutableMapOf<String, Double>()

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val oneWeekAgo = cal.timeInMillis
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val oneMonthAgo = cal.timeInMillis

        var countToday = 0
        var countThisWeek = 0
        var countThisMonth = 0

        // Latest balances across distinct accounts/providers
        val latestAccountBalances = mutableMapOf<String, Double>()

        for (tx in transactions) {
            val amount = tx.amount
            val type = TransactionType.fromString(tx.transactionType)

            // Balance calculation
            if (tx.balanceAfterTransaction != null && !latestAccountBalances.containsKey(tx.account)) {
                latestAccountBalances[tx.account] = tx.balanceAfterTransaction
            }

            // Categorization
            when (type) {
                TransactionType.DEPOSIT -> {
                    totalDeposits += amount
                    totalIncome += amount
                }
                TransactionType.TRANSFER_IN -> {
                    totalTransfers += amount
                    totalIncome += amount
                }
                TransactionType.COMMISSION -> {
                    totalIncome += amount
                }
                TransactionType.REVERSAL -> {
                    totalIncome += amount
                }
                TransactionType.WITHDRAWAL -> {
                    totalWithdrawals += amount
                    totalExpenses += amount
                }
                TransactionType.TRANSFER_OUT -> {
                    totalTransfers += amount
                    totalExpenses += amount
                }
                TransactionType.PAYMENT -> {
                    totalExpenses += amount
                }
                TransactionType.FEE -> {
                    totalFees += amount
                    totalExpenses += amount
                }
                else -> {
                    if (type.isCredit) totalIncome += amount else totalExpenses += amount
                }
            }

            // Provider breakdown
            providerMap[tx.provider] = (providerMap[tx.provider] ?: 0.0) + amount

            // Category breakdown
            categoryMap[tx.category] = (categoryMap[tx.category] ?: 0.0) + amount

            // Date counters
            if (tx.transactionDate == todayStr) countToday++
            if (tx.createdAt >= oneWeekAgo) countThisWeek++
            if (tx.createdAt >= oneMonthAgo) countThisMonth++
        }

        val totalBalance = if (latestAccountBalances.isNotEmpty()) {
            latestAccountBalances.values.sum()
        } else {
            (totalIncome - totalExpenses).coerceAtLeast(0.0)
        }

        val netCashFlow = totalIncome - totalExpenses

        return DashboardSummary(
            totalBalance = totalBalance,
            totalDeposits = totalDeposits,
            totalWithdrawals = totalWithdrawals,
            totalTransfers = totalTransfers,
            totalExpenses = totalExpenses,
            totalIncome = totalIncome,
            totalFees = totalFees,
            netCashFlow = netCashFlow,
            countToday = countToday,
            countThisWeek = countThisWeek,
            countThisMonth = countThisMonth,
            providerTotals = providerMap,
            categoryTotals = categoryMap
        )
    }

    /**
     * Seeds initial Ethiopian banking & mobile-money transactions so the user
     * immediately experiences a rich, populated dashboard upon first run.
     */
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        if (transactionDao.getCount() > 0) return@withContext

        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val sampleMessages = listOf(
            Triple("127", "Dear customer, you have received ETB 3,500.00 from 251911223344 (Abebe Bikila) on 02/10/2026 14:15:20. Your transaction number is CR99118822. Your current balance is ETB 7,850.50.", now - (day * 0)),
            Triple("CBE", "Dear Customer, Your A/C 1000123456789 has been credited with ETB 12,000.00 on 01-10-2026 by CBE Birr ref FT26275XYZ12. Your current balance is ETB 28,450.00.", now - (day * 1)),
            Triple("127", "Payment of ETB 650.00 to merchant Fresh Corner Supermarket (Code: 1042) was successful. Transaction number is PM44332211. Your current balance is ETB 7,200.50.", now - (day * 1)),
            Triple("AwashBank", "Dear Customer, your Account 01320123456700 has been debited with ETB 1,200.00 for POS payment at Kaldi Coffee. Current balance ETB 8,300.00. Ref: AW908124.", now - (day * 2)),
            Triple("127", "You have transferred ETB 500.00 to 251922334455 (Tigist Alemu) with transaction number TR88776655 on 29/09/2026. Your current balance is ETB 6,698.50. Service fee is ETB 2.00.", now - (day * 3)),
            Triple("CBE", "Dear Customer, Your A/C 1000123456789 has been debited with ETB 2,000.00 on 28-09-2026 at ATM Bole Medhanialem. Available Bal: ETB 26,450.00. Ref: DB992211.", now - (day * 4)),
            Triple("127", "You have purchased airtime of ETB 150.00 for 0911223344. Txn: AT776655. Balance is ETB 6,548.50.", now - (day * 5)),
            Triple("Dashen", "Dear Customer, your account 1234567890 has been credited with ETB 4,200.00 via Amole on 26/09/2026. Current Bal: ETB 11,500.00. Ref: AM991122.", now - (day * 6))
        )

        for ((sender, text, timestamp) in sampleMessages) {
            val parsed = ProviderRegistry.processMessage(sender, text, timestamp)
            if (parsed != null) {
                val entity = ProviderRegistry.toEntity(parsed, userId = "local_user", source = "SMS").copy(createdAt = timestamp)
                transactionDao.insertTransaction(entity)
            }
        }
    }
}
