package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FinancialProvider
import com.example.model.TransactionEntity
import com.example.model.TransactionType
import com.example.ui.MainViewModel
import com.example.ui.SortOrder
import com.example.ui.components.AddTransactionDialog
import com.example.ui.components.TransactionCard
import com.example.ui.components.TransactionDetailDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProviderFilter.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSortOrder.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val providerOptions = listOf("ALL") + FinancialProvider.values().filter { it != FinancialProvider.OTHER }.map { it.displayName }
    val typeOptions = listOf("ALL", "DEPOSIT", "WITHDRAWAL", "TRANSFER_IN", "TRANSFER_OUT", "PAYMENT", "FEE")

    fun shareCsv() {
        val header = "ID,Date,Time,Provider,Account,Type,Category,Amount,Currency,RefNumber,Sender,Receiver,BalanceAfter\n"
        val rows = allTransactions.joinToString("\n") { tx ->
            "\"${tx.id}\",\"${tx.transactionDate}\",\"${tx.transactionTime}\",\"${tx.provider}\",\"${tx.account}\",\"${tx.transactionType}\",\"${tx.category}\",${tx.amount},\"${tx.currency}\",\"${tx.referenceNumber ?: ""}\",\"${tx.sender ?: ""}\",\"${tx.receiver ?: ""}\",${tx.balanceAfterTransaction ?: ""}"
        }
        val csvContent = header + rows

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvContent)
            type = "text/csv"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Transactions CSV")
        context.startActivity(shareIntent)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_transaction_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("transactions_screen")
        ) {
            // 1. Search Bar & Export / Sort actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search by ref, sender, note...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("transaction_search_input")
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box {
                    IconButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier.testTag("sort_transactions_button")
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Sort")
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.displayName,
                                        fontWeight = if (selectedSort == order) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    viewModel.selectedSortOrder.value = order
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { shareCsv() },
                    modifier = Modifier.testTag("export_csv_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export CSV")
                }
            }

            // 2. Provider Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providerOptions.forEach { opt ->
                    FilterChip(
                        selected = selectedProvider == opt,
                        onClick = { viewModel.selectedProviderFilter.value = opt },
                        label = { Text(if (opt == "ALL") "All Banks" else opt, fontSize = 12.sp) }
                    )
                }
            }

            // 3. Type Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                typeOptions.forEach { opt ->
                    val displayName = when (opt) {
                        "ALL" -> "All Types"
                        "TRANSFER_IN" -> "Received"
                        "TRANSFER_OUT" -> "Sent"
                        else -> opt.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    FilterChip(
                        selected = selectedType == opt,
                        onClick = { viewModel.selectedTypeFilter.value = opt },
                        label = { Text(displayName, fontSize = 11.sp) }
                    )
                }
            }

            // Count header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${transactions.size} transactions found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                if (selectedProvider != "ALL" || selectedType != "ALL" || searchQuery.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.selectedProviderFilter.value = "ALL"
                            viewModel.selectedTypeFilter.value = "ALL"
                            viewModel.searchQuery.value = ""
                        }
                    ) {
                        Text("Reset Filters", fontSize = 11.sp)
                    }
                }
            }

            // 4. Transactions List
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching transactions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try adjusting your search query or provider filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        TransactionCard(
                            transaction = tx,
                            onClick = { selectedTransaction = tx }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedTransaction?.let { tx ->
        TransactionDetailDialog(
            transaction = tx,
            onDismiss = { selectedTransaction = null },
            onDelete = { id -> viewModel.deleteTransaction(id) }
        )
    }

    // Manual Add Dialog
    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { provider, amount, type, category, account, receiver, sender, ref, desc ->
                viewModel.addManualTransaction(provider, amount, type, category, account, receiver, sender, ref, desc)
            }
        )
    }
}
