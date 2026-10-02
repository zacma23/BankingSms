package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DashboardSummary
import com.example.ui.theme.AwashOrange
import com.example.ui.theme.CbePurple
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TelebirrCyan

@Composable
fun CashFlowComparisonCard(
    summary: DashboardSummary,
    modifier: Modifier = Modifier
) {
    val total = summary.totalIncome + summary.totalExpenses
    val incomeRatio = if (total > 0) (summary.totalIncome / total).toFloat() else 0.5f
    val animatedIncomeRatio by animateFloatAsState(targetValue = incomeRatio, label = "incomeRatio")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Cash Flow & Ratio",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Two-tone progress bar (Green for Income, Red for Expense)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(ExpenseRed)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedIncomeRatio)
                        .background(IncomeGreen)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(IncomeGreen))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Total Income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        text = "ETB %,.2f".format(summary.totalIncome),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = IncomeGreen
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ExpenseRed))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Total Expenses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        text = "ETB %,.2f".format(summary.totalExpenses),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ExpenseRed
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderDistributionCard(
    providerTotals: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    val totalVolume = providerTotals.values.sum()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions by Provider",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Total: ETB %,.0f".format(totalVolume),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (providerTotals.isEmpty()) {
                Text(
                    text = "No provider transactions recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                providerTotals.entries.sortedByDescending { it.value }.forEach { (provider, amount) ->
                    val ratio = if (totalVolume > 0) (amount / totalVolume).toFloat() else 0f
                    val percentage = (ratio * 100).toInt()

                    val barColor = when {
                        provider.contains("telebirr", ignoreCase = true) -> TelebirrCyan
                        provider.contains("cbe", ignoreCase = true) -> CbePurple
                        provider.contains("awash", ignoreCase = true) -> AwashOrange
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = provider,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ETB %,.0f ($percentage%)".format(amount),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = barColor,
                            trackColor = barColor.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDistributionCard(
    categoryTotals: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    val total = categoryTotals.values.sum()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Spending & Category Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (categoryTotals.isEmpty()) {
                Text(
                    text = "No category data available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                categoryTotals.entries.sortedByDescending { it.value }.take(5).forEach { (category, amount) ->
                    val ratio = if (total > 0) (amount / total).toFloat() else 0f
                    val percentage = (ratio * 100).toInt()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "ETB %,.0f (%d%%)".format(amount, percentage),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
