package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FinancialProvider
import com.example.model.TransactionType
import com.example.parser.CbeParser
import com.example.parser.ProviderRegistry
import com.example.parser.TelebirrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FinEthio SMS Tracker", appName)
  }

  @Test
  fun `telebirr parser parses incoming deposit SMS correctly`() {
    val parser = TelebirrParser()
    val sms = "Dear customer, you have received ETB 1,500.00 from 251911223344 (Abebe Bikila) on 02/10/2026 14:15:20. Your transaction number is CR123456789. Your current balance is ETB 3,450.50."
    assertTrue(parser.canParse("127", sms))

    val parsed = parser.parse("127", sms, System.currentTimeMillis())
    assertNotNull(parsed)
    assertEquals(1500.0, parsed!!.amount, 0.01)
    assertEquals("ETB", parsed.currency)
    assertEquals("CR123456789", parsed.referenceNumber)
    assertEquals(TransactionType.TRANSFER_IN, parsed.transactionType)
    assertEquals(3450.50, parsed.balanceAfterTransaction ?: 0.0, 0.01)
  }

  @Test
  fun `cbe parser parses debit ATM SMS correctly`() {
    val parser = CbeParser()
    val sms = "Dear Customer, Your A/C 1000123456789 has been debited with ETB 2,000.00 on 02-10-2026 at ATM Bole Branch. Available Bal: ETB 16,200.25. Ref: DB992211."
    assertTrue(parser.canParse("CBE", sms))

    val parsed = parser.parse("CBE", sms, System.currentTimeMillis())
    assertNotNull(parsed)
    assertEquals(2000.0, parsed!!.amount, 0.01)
    assertEquals("DB992211", parsed.referenceNumber)
    assertEquals(TransactionType.WITHDRAWAL, parsed.transactionType)
  }

  @Test
  fun `provider registry prevents duplicate fingerprints`() {
    val hash1 = ProviderRegistry.computeFingerprint("Telebirr", "CR12345", "test message 1")
    val hash2 = ProviderRegistry.computeFingerprint("Telebirr", "CR12345", "test message 1 repeated")
    assertEquals(hash1, hash2)
  }

  @Test
  fun `bank sender identifier recognizes known bank senders and rejects random senders`() {
    // Known banks
    assertTrue(com.example.receiver.BankSenderIdentifier.isKnownBankSender("127", "Test"))
    assertTrue(com.example.receiver.BankSenderIdentifier.isKnownBankSender("telebirr", "Test"))
    assertTrue(com.example.receiver.BankSenderIdentifier.isKnownBankSender("CBE", "Test"))
    assertTrue(com.example.receiver.BankSenderIdentifier.isKnownBankSender("AwashBank", "Test"))
    assertTrue(com.example.receiver.BankSenderIdentifier.isKnownBankSender("Dashen", "Test"))
    assertTrue(com.example.receiver.BankSenderIdentifier.isKnownBankSender("BOA", "Test"))

    // Non-bank senders without bank keywords
    org.junit.Assert.assertFalse(com.example.receiver.BankSenderIdentifier.isKnownBankSender("Friend", "Hey, are you free today?"))
    org.junit.Assert.assertFalse(com.example.receiver.BankSenderIdentifier.isKnownBankSender("+251911000000", "Hello! Let's meet at 5."))
  }
}
