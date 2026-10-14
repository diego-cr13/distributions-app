package com.distribuidora.vendedor.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distribuidora.vendedor.data.local.OrderItemRow
import com.distribuidora.vendedor.data.local.OrderSummary
import com.distribuidora.vendedor.data.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** RF-5: orders list (pending first, newest first), pending counter and detail. */
class OrdersViewModel(private val orders: OrderRepository) : ViewModel() {

    /** null while Room has not answered yet. */
    val summaries: StateFlow<List<OrderSummary>?> = orders.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingCount: StateFlow<Int> = orders.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun order(id: String): Flow<OrderSummary?> = orders.observeSummary(id)

    fun items(id: String): Flow<List<OrderItemRow>> = orders.observeItems(id)
}
