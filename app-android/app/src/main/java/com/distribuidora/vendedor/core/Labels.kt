package com.distribuidora.vendedor.core

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.distribuidora.vendedor.R

/**
 * Order status values. `PENDING` only exists on the phone; the other four are the
 * values of the `pedidos.estado` column and stay in Spanish on purpose.
 */
object OrderStatus {
    const val PENDING = "pendiente"
    const val SENT = "enviado"
    const val CONFIRMED = "confirmado"
    const val WITH_PROBLEM = "con_problema"
    const val REJECTED = "rechazado"
}

/** Per-item problem values of `pedido_items.problema`. */
object ItemProblem {
    const val OUT_OF_STOCK = "sin_stock"
    const val PRICE_CHANGED = "precio_cambiado"
}

/** Status → RF-5 label ("Pendiente de envío", "Enviado", ...). */
@StringRes
fun statusLabelRes(status: String): Int = when (status) {
    OrderStatus.PENDING -> R.string.status_pending
    OrderStatus.SENT -> R.string.status_sent
    OrderStatus.CONFIRMED -> R.string.status_confirmed
    OrderStatus.WITH_PROBLEM -> R.string.status_with_problem
    OrderStatus.REJECTED -> R.string.status_rejected
    else -> R.string.status_unknown
}

/** Problem → text ("Sin stock", "Precio cambiado"); null when the item has no problem. */
@StringRes
fun problemLabelRes(problem: String?): Int? = when (problem) {
    null -> null
    ItemProblem.OUT_OF_STOCK -> R.string.problem_out_of_stock
    ItemProblem.PRICE_CHANGED -> R.string.problem_price_changed
    else -> R.string.problem_unknown
}

/** Status badge colors: pending grey, sent blue, confirmed green, problem amber, rejected red. */
fun statusColor(status: String): Color = when (status) {
    OrderStatus.PENDING -> Color(0xFF757575)
    OrderStatus.SENT -> Color(0xFF1E88E5)
    OrderStatus.CONFIRMED -> Color(0xFF2E7D32)
    OrderStatus.WITH_PROBLEM -> Color(0xFFF59E0B)
    OrderStatus.REJECTED -> Color(0xFFD32F2F)
    else -> Color(0xFF757575)
}
