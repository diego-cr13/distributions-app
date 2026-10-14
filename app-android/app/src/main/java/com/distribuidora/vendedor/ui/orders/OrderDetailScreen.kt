package com.distribuidora.vendedor.ui.orders

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.core.formatBs
import com.distribuidora.vendedor.core.formatLaPaz
import com.distribuidora.vendedor.core.problemLabelRes
import com.distribuidora.vendedor.core.toMoney
import com.distribuidora.vendedor.data.local.OrderItemRow

/**
 * RF-5 detail: header with status, client, date and total; items as
 * "3 × Arroz grano de oro 1 kg" with price seen, price applied and per-item problem;
 * server reason and supervisor note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    viewModel: OrdersViewModel,
    onBack: () -> Unit,
) {
    val summary by remember(orderId) { viewModel.order(orderId) }.collectAsStateWithLifecycle(null)
    val items by remember(orderId) { viewModel.items(orderId) }.collectAsStateWithLifecycle(emptyList())

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.order_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val current = summary ?: return@Scaffold
        val order = current.order
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = current.clientName ?: order.clientId,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(order.status)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.order_detail_created, formatLaPaz(order.createdOnDevice)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    order.receivedAt?.let {
                        Text(
                            text = stringResource(R.string.order_detail_received, formatLaPaz(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.order_detail_total, formatBs(order.totalBs.toMoney())),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    order.reason?.let {
                        Spacer(Modifier.height(8.dp))
                        LabeledText(stringResource(R.string.order_detail_reason), it)
                    }
                    order.supervisorNote?.let {
                        Spacer(Modifier.height(8.dp))
                        LabeledText(stringResource(R.string.order_detail_supervisor_note), it)
                    }
                    order.lastError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.order_detail_last_error, it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.order_detail_items),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(items, key = { it.item.productId }) { row ->
                ItemRow(row)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ItemRow(row: OrderItemRow) {
    val item = row.item
    val problemRes = problemLabelRes(item.problem)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${item.quantity} × ${row.productName ?: item.productId}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatBs(item.subtotalBs.toMoney()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        val applied = item.priceAppliedBs
        Text(
            text = if (applied != null && applied != item.priceSeenBs) {
                stringResource(
                    R.string.order_item_prices_changed,
                    formatBs(item.priceSeenBs.toMoney()),
                    formatBs(applied.toMoney()),
                )
            } else {
                stringResource(R.string.order_item_price_seen, formatBs(item.priceSeenBs.toMoney()))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (problemRes != null) {
            Text(
                text = stringResource(problemRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
