package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.DetailRow
import com.example.ui.components.ProviderBadge
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FeeAmber
import com.example.ui.theme.IncomeGreen

data class PresetMessage(
    val title: String,
    val sender: String,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSimulatorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val presets = remember {
        listOf(
            PresetMessage(
                "Telebirr Deposit",
                "127",
                "Dear customer, you have received ETB 2,500.00 from 251911223344 (Abebe Bikila) on 02/10/2026 14:15:20. Your transaction number is CR55443322. Your current balance is ETB 9,450.50."
            ),
            PresetMessage(
                "Telebirr Transfer Out",
                "127",
                "You have transferred ETB 400.00 to 251922334455 (Tigist Alemu) with transaction number TR11223344 on 02/10/2026 11:05. Your current balance is ETB 9,048.50. Service fee is ETB 2.00."
            ),
            PresetMessage(
                "Telebirr Merchant Pay",
                "127",
                "Payment of ETB 780.00 to merchant Sheger Supermarket (Code: 10928) was successful. Transaction number is PM88776655. Your current balance is ETB 8,268.50."
            ),
            PresetMessage(
                "CBE Deposit (Credit)",
                "CBE",
                "Dear Customer, Your A/C 1000123456789 has been credited with ETB 8,000.00 on 02-10-2026 09:30:15 by CBE Birr ref FT26275XYZ12. Your current balance is ETB 34,200.25."
            ),
            PresetMessage(
                "CBE ATM Debit",
                "CBE",
                "Dear Customer, Your A/C 1000123456789 has been debited with ETB 3,000.00 on 02-10-2026 at ATM Piassa Branch. Available Bal: ETB 31,200.25. Ref: DB554433."
            ),
            PresetMessage(
                "Awash Bank Credit",
                "AwashBank",
                "Dear Customer, your Account 01320123456700 has been credited with ETB 6,500.00 on 02/10/2026. Current balance ETB 14,800.00. Ref: AW908123."
            ),
            PresetMessage(
                "Dashen Amole Credit",
                "Dashen",
                "Dear Customer, your account 1234567890 has been credited with ETB 3,200.00 via Amole. Current Bal: ETB 8,500.00. Ref: AM991122."
            )
        )
    }

    var senderText by remember { mutableStateOf(presets[0].sender) }
    var bodyText by remember { mutableStateOf(presets[0].message) }

    val simulatorResult by viewModel.simulatorResult.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sms_simulator_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Bank SMS Parser & Pipeline Simulator",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Simulate receiving a bank SMS or paste your own to test real-time parsing, validation, duplicate check, and database storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Preset Chips
        item {
            Text(
                text = "Choose Sample Ethiopian Bank SMS:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = bodyText == preset.message,
                        onClick = {
                            senderText = preset.sender
                            bodyText = preset.message
                            viewModel.clearSimulatorResult()
                        },
                        label = { Text(preset.title, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Sender input & SMS textarea
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = senderText,
                        onValueChange = { senderText = it },
                        label = { Text("Sender Header / Number (e.g. 127, CBE, AwashBank)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulator_sender_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        label = { Text("SMS Message Body") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulator_message_input"),
                        minLines = 4,
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.simulateSmsParsing(senderText, bodyText, saveToDb = false)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulator_preview_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Test", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Parse & Preview")
                        }

                        Button(
                            onClick = {
                                viewModel.simulateSmsParsing(senderText, bodyText, saveToDb = true)
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("simulator_save_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Parse & Save to DB")
                        }
                    }
                }
            }
        }

        // Parsing Result Inspection Card
        item {
            simulatorResult?.let { res ->
                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulator_result_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Parser Pipeline Output",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (res.isDuplicate) {
                                Surface(
                                    color = FeeAmber.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = "Duplicate", tint = FeeAmber, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Duplicate Prevented", color = FeeAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (res.savedEntity != null) {
                                Surface(
                                    color = IncomeGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Saved", tint = IncomeGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Saved to Database", color = IncomeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (res.errorMessage != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = "Error", tint = ExpenseRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(res.errorMessage, color = ExpenseRed, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (res.parsedTransaction != null) {
                            val parsed = res.parsedTransaction

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProviderBadge(providerName = parsed.provider.displayName)
                                Text(
                                    text = "%,.2f %s".format(parsed.amount, parsed.currency),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (parsed.transactionType.isCredit) IncomeGreen else ExpenseRed
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            DetailRow("Transaction Type", parsed.transactionType.displayName)
                            DetailRow("Category", parsed.category)
                            DetailRow("Account", parsed.account)
                            if (!parsed.sender.isNullOrBlank()) DetailRow("Sender", parsed.sender)
                            if (!parsed.receiver.isNullOrBlank()) DetailRow("Receiver", parsed.receiver)
                            if (!parsed.referenceNumber.isNullOrBlank()) DetailRow("Reference No", parsed.referenceNumber)
                            if (parsed.balanceAfterTransaction != null) DetailRow("Balance After", "%,.2f %s".format(parsed.balanceAfterTransaction, parsed.currency))
                            DetailRow("Extracted Date", "${parsed.transactionDate} at ${parsed.transactionTime}")
                        }
                    }
                }
            }
        }
    }
}
