package com.example.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.FinEthioApp
import com.example.MainActivity
import com.example.R
import com.example.data.ProcessingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages recognized sender IDs for Ethiopian banks and mobile-money providers.
 */
object BankSenderIdentifier {
    val KNOWN_BANK_SENDER_IDS = setOf(
        // Telebirr / Ethio Telecom
        "127", "telebirr", "ethiotelecom", "ethio telecom", "ethio_telecom",
        // Commercial Bank of Ethiopia (CBE)
        "cbe", "cbebirr", "cbe birr", "cbe_birr", "commercial bank", "951", "891",
        // Awash Bank
        "awash", "awashbank", "awash bank", "awash_bank", "8989", "8900",
        // Dashen Bank / Amole
        "dashen", "dashenbank", "dashen bank", "amole",
        // Bank of Abyssinia
        "boa", "abyssinia", "bankofabyssinia", "apollo",
        // Additional Ethiopian financial institutions
        "hibret", "united bank", "wegagen", "nib", "nib bank", "coop", "coop bank",
        "berhan", "zemen", "lion", "anbessa", "siinqee", "oromia", "bunna", "global", "hijra", "tsehay"
    )

    fun isKnownBankSender(sender: String, messageBody: String): Boolean {
        val cleanSender = sender.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
        val rawSenderLower = sender.lowercase().trim()

        // 1. Direct match with known bank sender IDs, shortcodes, or tags
        if (KNOWN_BANK_SENDER_IDS.any { rawSenderLower.contains(it) || cleanSender.contains(it) }) {
            return true
        }

        // 2. Alphatag sender containing banking keywords (e.g. Bank, Birr, Pay)
        val isAlphaSender = sender.any { it.isLetter() }
        if (isAlphaSender && (rawSenderLower.contains("bank") || rawSenderLower.contains("birr") || rawSenderLower.contains("pay"))) {
            return true
        }

        // 3. Fallback: message body explicitly includes verified bank headers
        val lowerBody = messageBody.lowercase()
        return lowerBody.contains("telebirr") ||
                lowerBody.contains("cbe birr") ||
                lowerBody.contains("commercial bank") ||
                lowerBody.contains("awash bank") ||
                lowerBody.contains("dashen bank") ||
                lowerBody.contains("bank of abyssinia")
    }
}

/**
 * Android BroadcastReceiver that listens for incoming SMS messages,
 * checks if the sender matches known bank sender IDs, and triggers
 * parsing to extract transaction data and persist it into the database.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        val fullBody = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        Log.d("FinEthioSms", "Incoming SMS received from sender: $sender")

        // 1. Verify if the sender matches known bank sender IDs or banking indicators
        if (!BankSenderIdentifier.isKnownBankSender(sender, fullBody)) {
            Log.d("FinEthioSms", "Ignored non-bank SMS from: $sender")
            return
        }

        Log.i("FinEthioSms", "Bank SMS detected from $sender! Triggering transaction parser...")

        val app = context.applicationContext as? FinEthioApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 2. Trigger transaction parsing, validation, duplicate check, and database storage
                val result = app.repository.processIncomingMessage(
                    sender = sender,
                    message = fullBody,
                    timestampMillis = timestamp,
                    source = "SMS"
                )

                when (result) {
                    is ProcessingResult.Success -> {
                        val tx = result.transaction
                        if (!result.isDuplicate) {
                            Log.i("FinEthioSms", "Transaction saved: ${tx.provider} - ${tx.amount} ${tx.currency} (${tx.transactionType})")
                            showTransactionNotification(
                                context = context,
                                provider = tx.provider,
                                amount = tx.amount,
                                currency = tx.currency,
                                details = tx.description ?: tx.category,
                                notificationId = tx.id.hashCode()
                            )
                        } else {
                            Log.w("FinEthioSms", "Duplicate transaction detected for ref: ${tx.referenceNumber}. Skipped duplicate insertion.")
                        }
                    }
                    is ProcessingResult.Failure -> {
                        Log.w("FinEthioSms", "Parsing skipped: ${result.reason}")
                    }
                }
            } catch (e: Exception) {
                Log.e("FinEthioSms", "Error executing SMS parsing pipeline", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showTransactionNotification(
        context: Context,
        provider: String,
        amount: Double,
        currency: String,
        details: String,
        notificationId: Int
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = "%,.2f %s".format(amount, currency)
        val title = "FinEthio: $provider ($formattedAmount)"

        val builder = NotificationCompat.Builder(context, FinEthioApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$formattedAmount\n$details"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w("FinEthioSms", "Notification permission not granted: ${e.message}")
        }
    }
}
