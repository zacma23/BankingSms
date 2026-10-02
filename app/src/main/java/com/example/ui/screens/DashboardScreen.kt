package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.TransactionEntity
import com.example.ui.MainViewModel
import com.example.ui.components.AddTransactionDialog
import com.example.ui.components.CashFlowComparisonCard
import com.example.ui.components.CategoryDistributionCard
import com.example.ui.components.ProviderDistributionCard
import com.example.ui.components.TransactionCard
import com.example.ui.components.TransactionDetailDialog
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FeeAmber
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TelebirrCyan

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToSimulator: () -> Unit,
    onNavigateToAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Total Balance Card with Gradient
        item {
            ElevatedCard(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("total_balance_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0A2540),
                                    Color(0xFF0F3A66),
                                    Color(0xFF026AA7)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NET AVAILABLE BALANCE",
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                                color = Color(0xFFBAE6FD)
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "ETB / Birr",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "%,.2f".format(summary.totalBalance),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick mini stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Today's Txns", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                Text("${summary.countToday}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Column {
                                Text("This Month", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                Text("${summary.countThisMonth}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Column {
                                Text("Net Cash Flow", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                Text(
                                    text = "%s%,.0f".format(if (summary.netCashFlow >= 0) "+" else "", summary.netCashFlow),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (summary.netCashFlow >= 0) IncomeGreen else ExpenseRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Action Shortcuts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToSimulator,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_simulator_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Simulator", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test SMS", fontSize = 12.sp)
                }

                Button(
                    onClick = onNavigateToAi,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_ai_advisor_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Advisor", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_add_manual_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Txn", fontSize = 12.sp)
                }
            }
        }

        // 3. Financial Metrics & Fee Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Total Transfers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ETB %,.0f".format(summary.totalTransfers),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Service Fees Paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ETB %,.2f".format(summary.totalFees),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = FeeAmber
                        )
                    }
                }
            }
        }

        // 4. Visual Charts: Cash Flow & Provider Distribution
        item {
            CashFlowComparisonCard(summary = summary)
        }

        item {
            ProviderDistributionCard(providerTotals = summary.providerTotals)
        }

        item {
            CategoryDistributionCard(categoryTotals = summary.categoryTotals)
        }

        // 5. Recent Transactions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Text("View All (${allTransactions.size})")
                    Icon(Icons.Default.ArrowForward, contentDescription = "View all", modifier = Modifier.size(16.dp))
                }
            }
        }

        val recentList = allTransactions.take(5)
        if (recentList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No transactions detected yet. Use 'Test SMS' to simulate or send a transaction SMS.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            items(recentList, key = { it.id }) { tx ->
                TransactionCard(
                    transaction = tx,
                    onClick = { selectedTransaction = tx }
                )
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

    // Add Manual Transaction Dialog
    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { provider, amount, type, category, account, receiver, sender, ref, desc ->
                viewModel.addManualTransaction(provider, amount, type, category, account, receiver, sender, ref, desc)
            }
        )
    }
}
