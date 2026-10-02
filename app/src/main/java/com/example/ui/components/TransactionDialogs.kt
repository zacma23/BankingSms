package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.FinancialProvider
import com.example.model.TransactionCategory
import com.example.model.TransactionEntity
import com.example.model.TransactionType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

@Composable
fun TransactionDetailDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val type = TransactionType.fromString(transaction.transactionType)
    val amountColor = if (type.isCredit) IncomeGreen else ExpenseRed
    val prefix = if (type.isCredit) "+" else "-"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProviderBadge(providerName = transaction.provider)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "$prefix %,.2f %s".format(transaction.amount, transaction.currency),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )

                Text(
                    text = transaction.description ?: type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailRow("Type", type.displayName)
                DetailRow("Category", transaction.category)
                DetailRow("Account / Wallet", transaction.account)

                if (!transaction.sender.isNullOrBlank()) {
                    DetailRow("Sender", transaction.sender)
                }
                if (!transaction.receiver.isNullOrBlank()) {
                    DetailRow("Receiver", transaction.receiver)
                }

                if (!transaction.referenceNumber.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reference No",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = transaction.referenceNumber,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Reference", transaction.referenceNumber))
                                    Toast.makeText(context, "Reference number copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.padding(0.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Ref", modifier = Modifier.width(18.dp))
                            }
                        }
                    }
                }

                DetailRow("Date & Time", "${transaction.transactionDate} at ${transaction.transactionTime}")

                if (transaction.balanceAfterTransaction != null) {
                    DetailRow("Balance After", "%,.2f %s".format(transaction.balanceAfterTransaction, transaction.currency))
                }

                DetailRow("Source", "${transaction.source} (Verified: ${if (transaction.isVerified) "Yes" else "No"})")

                if (!transaction.rawMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Raw Notification / SMS",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = transaction.rawMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            onDelete(transaction.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                        modifier = Modifier.testTag("delete_transaction_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", color = ExpenseRed)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onAdd: (provider: String, amount: Double, type: String, category: String, account: String, receiver: String?, sender: String?, ref: String?, description: String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(FinancialProvider.TELEBIRR.displayName) }
    var selectedType by remember { mutableStateOf(TransactionType.PAYMENT.name) }
    var selectedCategory by remember { mutableStateOf(TransactionCategory.SHOPPING) }
    var accountText by remember { mutableStateOf("") }
    var recipientText by remember { mutableStateOf("") }
    var refText by remember { mutableStateOf("") }
    var descText by remember { mutableStateOf("") }

    var providerExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val providersList = FinancialProvider.values().map { it.displayName }
    val typesList = listOf(
        TransactionType.DEPOSIT.name,
        TransactionType.TRANSFER_IN.name,
        TransactionType.WITHDRAWAL.name,
        TransactionType.TRANSFER_OUT.name,
        TransactionType.PAYMENT.name,
        TransactionType.FEE.name
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Transaction Manually", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (ETB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_amount_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Provider dropdown
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProvider,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider / Bank") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        providersList.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    selectedProvider = p
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Transaction Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typesList.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    selectedType = t
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        TransactionCategory.allCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Description / Note") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = refText,
                    onValueChange = { refText = it },
                    label = { Text("Reference / Txn ID (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onAdd(
                            selectedProvider,
                            amount,
                            selectedType,
                            selectedCategory,
                            accountText,
                            recipientText.ifBlank { null },
                            null,
                            refText.ifBlank { null },
                            descText.ifBlank { null }
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("submit_manual_transaction_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
