package com.distribuidora.vendedor.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distribuidora.vendedor.Graph
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.core.formatBs
import com.distribuidora.vendedor.core.toMoney
import com.distribuidora.vendedor.data.local.ProductEntity
import com.distribuidora.vendedor.ui.common.EmptyMessage
import com.distribuidora.vendedor.ui.common.SearchField

/** RF-2: local catalog (code, name, unit, price, stock) with a search box; works offline. */
@Composable
fun CatalogScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    val products by remember(query) { Graph.db.productDao().observe(query.trim()) }
        .collectAsStateWithLifecycle(initialValue = null)

    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.catalog_search_hint),
        )
        val list = products
        when {
            list == null -> Unit // loading from Room
            list.isEmpty() -> EmptyMessage(
                text = if (query.isBlank()) stringResource(R.string.catalog_empty)
                else stringResource(R.string.search_no_results),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { product ->
                    ProductRow(product)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: ProductEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${product.code} · ${product.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatBs(product.priceBs.toMoney()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.catalog_stock, product.stock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
