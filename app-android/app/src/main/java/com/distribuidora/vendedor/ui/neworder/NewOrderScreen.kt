package com.distribuidora.vendedor.ui.neworder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.core.formatBs
import com.distribuidora.vendedor.core.toMoney
import com.distribuidora.vendedor.data.local.ClientEntity
import com.distribuidora.vendedor.data.local.ProductEntity
import com.distribuidora.vendedor.ui.common.EmptyMessage
import com.distribuidora.vendedor.ui.common.SearchField

private val AmberWarning = Color(0xFFB26A00)

/**
 * RF-3: pick a client, pick products with integer quantities, live total, Confirmar.
 * Quantities above the local stock show an amber notice but are not blocked (the
 * server decides, RF-6).
 */
@Composable
fun NewOrderScreen(
    viewModel: NewOrderViewModel,
    onCreated: (orderId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        val client = state.client
        if (client == null) {
            ClientPicker(
                query = state.clientQuery,
                onQueryChange = viewModel::onClientQueryChange,
                clients = clients,
                onSelect = viewModel::selectClient,
                modifier = Modifier.weight(1f),
            )
        } else {
            SelectedClientRow(client, onChange = { viewModel.selectClient(null) })
            HorizontalDivider()
            SearchField(
                value = state.productQuery,
                onValueChange = viewModel::onProductQueryChange,
                placeholder = stringResource(R.string.catalog_search_hint),
            )
            if (products.isEmpty()) {
                EmptyMessage(
                    text = stringResource(R.string.search_no_results),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(products, key = { it.id }) { product ->
                        ProductLine(
                            product = product,
                            quantity = state.lines[product.id]?.quantity ?: 0,
                            onQuantityChange = { viewModel.setQuantity(product, it) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        TotalBar(
            state = state,
            onConfirm = { viewModel.confirm(onCreated) },
        )
    }
}

@Composable
private fun ClientPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    clients: List<ClientEntity>,
    onSelect: (ClientEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.new_order_pick_client),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.clients_search_hint),
        )
        if (clients.isEmpty()) {
            EmptyMessage(stringResource(R.string.search_no_results))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(clients, key = { it.id }) { client ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(client) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(client.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = listOf(client.code, client.zone, client.city).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SelectedClientRow(client: ClientEntity, onChange: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.new_order_client),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(text = "${client.name} · ${client.code}", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.new_order_change_client)) }
    }
}

@Composable
private fun ProductLine(
    product: ProductEntity,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
) {
    val price = product.priceBs.toMoney()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${product.code} · ${formatBs(price)} · " +
                        stringResource(R.string.catalog_stock, product.stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            QuantityStepper(quantity = quantity, onChange = onQuantityChange)
        }
        if (quantity > product.stock) {
            Text(
                text = stringResource(R.string.new_order_stock_warning, product.stock),
                style = MaterialTheme.typography.bodySmall,
                color = AmberWarning,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** [-] [qty] [+]; the field accepts digits only, so the quantity is always an integer ≥ 0. */
@Composable
private fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onChange(quantity - 1) },
            enabled = quantity > 0,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.width(40.dp),
        ) { Text("−") }
        OutlinedTextField(
            value = if (quantity == 0) "" else quantity.toString(),
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(5)
                onChange(digits.toIntOrNull() ?: 0)
            },
            singleLine = true,
            placeholder = { Text("0", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            modifier = Modifier
                .width(64.dp)
                .padding(horizontal = 4.dp),
        )
        Button(
            onClick = { onChange(quantity + 1) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.width(40.dp),
        ) { Text("+") }
    }
}

@Composable
private fun TotalBar(state: NewOrderUiState, onConfirm: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.new_order_total, formatBs(state.total)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pluralStringResource(R.plurals.orders_item_count, state.itemCount, state.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(0.dp))
            Button(onClick = onConfirm, enabled = state.canConfirm) {
                Text(stringResource(R.string.new_order_confirm))
            }
        }
    }
}
