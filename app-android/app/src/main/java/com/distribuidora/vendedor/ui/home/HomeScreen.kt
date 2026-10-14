package com.distribuidora.vendedor.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distribuidora.vendedor.Graph
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.data.repository.SyncError
import com.distribuidora.vendedor.data.repository.SyncStatus
import com.distribuidora.vendedor.ui.catalog.CatalogScreen
import com.distribuidora.vendedor.ui.clients.ClientsScreen
import com.distribuidora.vendedor.ui.neworder.NewOrderScreen
import com.distribuidora.vendedor.ui.neworder.NewOrderViewModel
import com.distribuidora.vendedor.ui.orders.OrderDetailScreen
import com.distribuidora.vendedor.ui.orders.OrdersScreen
import com.distribuidora.vendedor.ui.orders.OrdersViewModel

/** Home: vendor header, "Salir", tabs Pedidos / Nuevo / Catálogo / Clientes, order detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel {
        HomeViewModel(
            Graph.authRepository,
            Graph.orderRepository,
            Graph.syncRepository,
            Graph.syncScheduler,
            Graph.sessionStore,
        )
    },
    ordersViewModel: OrdersViewModel = viewModel { OrdersViewModel(Graph.orderRepository) },
    newOrderViewModel: NewOrderViewModel = viewModel {
        NewOrderViewModel(Graph.orderRepository, Graph.syncScheduler, Graph.db.clientDao(), Graph.db.productDao())
    },
) {
    val vendor by viewModel.vendor.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val pendingCount by ordersViewModel.pendingCount.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(HomeTab.Orders) }
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val syncDoneText = stringResource(R.string.sync_done)
    val syncFailedText = stringResource(R.string.sync_failed)
    val noNetworkText = stringResource(R.string.login_error_no_network)

    LaunchedEffect(Unit) { viewModel.onEnter() }

    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        val res = message ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(res), duration = SnackbarDuration.Short)
        viewModel.consumeMessage()
    }

    // The status is cleared only after the snackbar was shown: clearing first would
    // restart this effect (keyed on the status) and cancel the snackbar.
    LaunchedEffect(syncStatus) {
        when (val status = syncStatus) {
            is SyncStatus.Success -> {
                snackbar.showSnackbar(syncDoneText, duration = SnackbarDuration.Short)
                viewModel.clearSyncStatus()
            }
            is SyncStatus.Failure -> {
                val detail = when (val error = status.error) {
                    is SyncError.Network -> noNetworkText
                    else -> error.message ?: ""
                }
                snackbar.showSnackbar("$syncFailedText: $detail", duration = SnackbarDuration.Long)
                viewModel.clearSyncStatus()
            }
            else -> Unit
        }
    }

    selectedOrderId?.let { orderId ->
        OrderDetailScreen(
            orderId = orderId,
            viewModel = ordersViewModel,
            onBack = { selectedOrderId = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = vendor?.name ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        vendor?.let {
                            Text(
                                text = it.code,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                },
                actions = {
                    if (syncStatus is SyncStatus.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::syncNow) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.home_sync_now),
                            )
                        }
                    }
                    TextButton(onClick = viewModel::logout) {
                        Text(stringResource(R.string.home_logout))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tab.ordinal) {
                HomeTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = {
                            val label = stringResource(entry.labelRes())
                            if (entry == HomeTab.Orders && pendingCount > 0) {
                                // Pending counter; the end padding keeps the badge off the text.
                                BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                                    Text(label, maxLines = 1, modifier = Modifier.padding(end = 8.dp))
                                }
                            } else {
                                Text(label, maxLines = 1)
                            }
                        },
                    )
                }
            }
            when (tab) {
                HomeTab.Orders -> OrdersScreen(
                    viewModel = ordersViewModel,
                    onOpen = { selectedOrderId = it },
                )
                HomeTab.NewOrder -> NewOrderScreen(
                    viewModel = newOrderViewModel,
                    onCreated = { tab = HomeTab.Orders },
                )
                HomeTab.Catalog -> CatalogScreen()
                HomeTab.Clients -> ClientsScreen()
            }
        }
    }
}

private fun HomeTab.labelRes(): Int = when (this) {
    HomeTab.Orders -> R.string.tab_orders
    HomeTab.NewOrder -> R.string.tab_new_order
    HomeTab.Catalog -> R.string.tab_catalog
    HomeTab.Clients -> R.string.tab_clients
}
