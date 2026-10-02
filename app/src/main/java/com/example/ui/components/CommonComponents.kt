package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FinancialProvider
import com.example.model.TransactionEntity
import com.example.model.TransactionType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FeeAmber
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TelebirrBlue
import com.example.ui.theme.TransferBlue

@Composable
fun ProviderBadge(providerName: String, modifier: Modifier = Modifier) {
    val (bg, text) = when {
        providerName.contains("telebirr", ignoreCase = true) -> Color(0xFF00ADEF) to Color.White
        providerName.contains("cbe", ignoreCase = true) -> Color(0xFF7A2082) to Color.White
        providerName.contains("awash", ignoreCase = true) -> Color(0xFFFF6B00) to Color.White
        providerName.contains("dashen", ignoreCase = true) -> Color(0xFF005696) to Color.White
        providerName.contains("abyssinia", ignoreCase = true) -> Color(0xFFEC671E) to Color.White
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = bg.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = providerName,
            color = bg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TransactionTypeIcon(type: TransactionType) {
    val (icon, tint, bg) = when (type) {
        TransactionType.DEPOSIT, TransactionType.TRANSFER_IN ->
            Triple(Icons.Default.ArrowDownward, IncomeGreen, IncomeGreen.copy(alpha = 0.15f))
        TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT ->
            Triple(Icons.Default.ArrowUpward, ExpenseRed, ExpenseRed.copy(alpha = 0.15f))
        TransactionType.PAYMENT ->
            Triple(Icons.Default.Payment, TelebirrBlue, TelebirrBlue.copy(alpha = 0.15f))
        TransactionType.FEE ->
            Triple(Icons.Default.Receipt, FeeAmber, FeeAmber.copy(alpha = 0.15f))
        TransactionType.REVERSAL ->
            Triple(Icons.Default.Loop, TransferBlue, TransferBlue.copy(alpha = 0.15f))
        TransactionType.FAILED ->
            Triple(Icons.Default.ErrorOutline, ExpenseRed, ExpenseRed.copy(alpha = 0.15f))
        else ->
            Triple(Icons.Default.CreditCard, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.displayName,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = TransactionType.fromString(transaction.transactionType)
    val amountPrefix = if (type.isCredit) "+" else "-"
    val amountColor = if (type.isCredit) IncomeGreen else ExpenseRed

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("transaction_card_${transaction.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionTypeIcon(type = type)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderBadge(providerName = transaction.provider)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = transaction.description ?: type.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${transaction.transactionDate}  ${transaction.transactionTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (!transaction.referenceNumber.isNullOrBlank()) {
                        Text(
                            text = " • Ref: ${transaction.referenceNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix %,.2f".format(transaction.amount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )
                Text(
                    text = transaction.currency,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (transaction.balanceAfterTransaction != null) {
                    Text(
                        text = "Bal: %,.0f".format(transaction.balanceAfterTransaction),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
