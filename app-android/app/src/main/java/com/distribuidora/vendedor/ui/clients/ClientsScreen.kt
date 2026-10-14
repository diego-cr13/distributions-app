package com.distribuidora.vendedor.ui.clients

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distribuidora.vendedor.Graph
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.data.local.ClientEntity
import com.distribuidora.vendedor.ui.common.EmptyMessage
import com.distribuidora.vendedor.ui.common.SearchField

/** RF-2: local clients (code, name, zone, city) with a search box; works offline. */
@Composable
fun ClientsScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    val clients by remember(query) { Graph.db.clientDao().observe(query.trim()) }
        .collectAsStateWithLifecycle(initialValue = null)

    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.clients_search_hint),
        )
        val list = clients
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyMessage(
                text = if (query.isBlank()) stringResource(R.string.clients_empty)
                else stringResource(R.string.search_no_results),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { client ->
                    ClientRow(client)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ClientRow(client: ClientEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = client.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = listOf(client.code, client.zone, client.city).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
