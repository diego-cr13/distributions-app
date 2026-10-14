package com.distribuidora.vendedor.ui.neworder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distribuidora.vendedor.core.subtotal
import com.distribuidora.vendedor.core.toMoney
import com.distribuidora.vendedor.core.total
import com.distribuidora.vendedor.data.local.ClientDao
import com.distribuidora.vendedor.data.local.ClientEntity
import com.distribuidora.vendedor.data.local.ProductDao
import com.distribuidora.vendedor.data.local.ProductEntity
import com.distribuidora.vendedor.data.repository.NewOrderLine
import com.distribuidora.vendedor.data.repository.OrderRepository
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state of RF-3. Quantities are keyed by product id; zero removes the line. */
data class NewOrderUiState(
    val client: ClientEntity? = null,
    val clientQuery: String = "",
    val productQuery: String = "",
    val lines: Map<String, NewOrderLine> = emptyMap(),
    val saving: Boolean = false,
) {
    val itemCount: Int get() = lines.size
    val total: BigDecimal
        get() = total(lines.values.map { subtotal(it.quantity, it.product.priceBs.toMoney()) })
    val canConfirm: Boolean get() = !saving && client != null && lines.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class NewOrderViewModel(
    private val orders: OrderRepository,
    clientDao: ClientDao,
    productDao: ProductDao,
) : ViewModel() {
    private val _state = MutableStateFlow(NewOrderUiState())
    val state: StateFlow<NewOrderUiState> = _state

    private val clientQuery = MutableStateFlow("")
    private val productQuery = MutableStateFlow("")

    val clients: StateFlow<List<ClientEntity>> = clientQuery
        .flatMapLatest { clientDao.observe(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<ProductEntity>> = productQuery
        .flatMapLatest { productDao.observe(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onClientQueryChange(value: String) {
        clientQuery.value = value
        _state.update { it.copy(clientQuery = value) }
    }

    fun selectClient(client: ClientEntity?) = _state.update { it.copy(client = client) }

    fun onProductQueryChange(value: String) {
        productQuery.value = value
        _state.update { it.copy(productQuery = value) }
    }

    /** Sets an integer quantity ≥ 0 for a product; 0 removes the line. */
    fun setQuantity(product: ProductEntity, quantity: Int) {
        val safe = quantity.coerceAtLeast(0)
        _state.update { current ->
            val lines = current.lines.toMutableMap()
            if (safe == 0) lines.remove(product.id) else lines[product.id] = NewOrderLine(product, safe)
            current.copy(lines = lines)
        }
    }

    fun quantityOf(productId: String): Int = _state.value.lines[productId]?.quantity ?: 0

    /** Saves the order as `pendiente`, clears the form and reports the new id. */
    fun confirm(onCreated: (orderId: String) -> Unit) {
        val current = _state.value
        val client = current.client ?: return
        if (!current.canConfirm) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = orders.createPendingOrder(client.id, current.lines.values.toList())
            clientQuery.value = ""
            productQuery.value = ""
            _state.value = NewOrderUiState()
            onCreated(id)
        }
    }
}
