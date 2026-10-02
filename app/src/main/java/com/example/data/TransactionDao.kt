package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE referenceNumber = :refNumber AND referenceNumber IS NOT NULL LIMIT 1")
    suspend fun findByReferenceNumber(refNumber: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE (:query = '' OR description LIKE '%' || :query || '%' OR referenceNumber LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' OR receiver LIKE '%' || :query || '%')
        AND (:provider = 'ALL' OR provider = :provider)
        AND (:type = 'ALL' OR transactionType = :type)
        AND (:category = 'ALL' OR category = :category)
        ORDER BY createdAt DESC
    """)
    fun filterTransactions(
        query: String,
        provider: String,
        type: String,
        category: String
    ): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
