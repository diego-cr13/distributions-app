package com.distribuidora.vendedor.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.core.formatBs
import com.distribuidora.vendedor.core.formatLaPaz
import com.distribuidora.vendedor.core.statusColor
import com.distribuidora.vendedor.core.statusLabelRes
import com.distribuidora.vendedor.core.toMoney
import com.distribuidora.vendedor.data.local.OrderSummary
import com.distribuidora.vendedor.ui.common.EmptyMessage

/** RF-5: every local order with La Paz time, client, item count, total and status badge. */
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    onOpen: (orderId: String) -> Unit,
) {
    val orders by viewModel.summaries.collectAsStateWithLifecycle()
    val list = orders

    when {
        list == null -> Unit
        list.isEmpty() -> EmptyMessage(stringResource(R.string.orders_empty))
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.order.id }) { summary ->
                OrderRow(summary, onClick = { onOpen(summary.order.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OrderRow(summary: OrderSummary, onClick: () -> Unit) {
    val order = summary.order
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.clientName ?: order.clientId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(order.status)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatLaPaz(order.createdOnDevice) + " · " +
                    pluralStringResource(R.plurals.orders_item_count, summary.itemCount, summary.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = formatBs(order.totalBs.toMoney()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        order.lastError?.let { error ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** RF-5 label on a tinted pill with the status color. */
@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Text(
        text = stringResource(statusLabelRes(status)),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
