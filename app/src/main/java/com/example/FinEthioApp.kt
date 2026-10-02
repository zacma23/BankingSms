package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FinEthioApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this) }

    val repository by lazy {
        val firestoreDb = try {
            val dbId = getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
        } catch (e: Throwable) {
            Log.w("FinEthioApp", "Firestore not initialized: ${e.message}")
            null
        }
        TransactionRepository(database.transactionDao(), firestoreDb)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Throwable) {
            Log.w("FinEthioApp", "FirebaseApp initialization: ${e.message}")
        }

        createNotificationChannel()

        // Seed realistic sample transactions on initial launch
        applicationScope.launch {
            try {
                repository.seedInitialDataIfEmpty()
            } catch (e: Throwable) {
                Log.w("FinEthioApp", "Initial data seed: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val name = "Transaction Alerts"
                val descriptionText = "Notifications for detected bank and mobile-money SMS transactions"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                }
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Throwable) {
                Log.w("FinEthioApp", "Notification channel creation: ${e.message}")
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "finethio_transactions"
        lateinit var instance: FinEthioApp
            private set
    }
}
