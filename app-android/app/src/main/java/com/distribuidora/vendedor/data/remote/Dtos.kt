package com.distribuidora.vendedor.data.remote

import com.distribuidora.vendedor.core.BigDecimalSerializer
import java.math.BigDecimal
import kotlinx.serialization.Serializable

/**
 * PostgREST DTOs. Column names stay in Spanish because they are the applied schema;
 * money travels through [BigDecimalSerializer] (never Double).
 */

/** Row of `perfiles` (only the vendor's own row is visible through RLS). */
@Serializable
data class ProfileDto(
    val id: String,
    val codigo: String,
    val nombre: String,
    val rol: String,
    val activo: Boolean,
) {
    val isVendor: Boolean get() = rol == ROLE_VENDOR

    companion object {
        const val ROLE_VENDOR = "vendedor"
    }
}

/** Row of `productos`. */
@Serializable
data class ProductDto(
    val id: String,
    val codigo: String,
    val nombre: String,
    val unidad: String,
    @Serializable(with = BigDecimalSerializer::class) val precio_bs: BigDecimal,
    val stock: Int,
    val actualizado_en: String,
)

/** Row of `clientes` (`zona` and `ciudad` are nullable in the schema). */
@Serializable
data class ClientDto(
    val id: String,
    val codigo: String,
    val nombre: String,
    val zona: String? = null,
    val ciudad: String? = null,
    val actualizado_en: String,
)

/** Row of `pedidos` with the `pedido_items(*)` embed. */
@Serializable
data class OrderDto(
    val id: String,
    val cliente_id: String,
    val estado: String,
    val motivo: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val total_bs: BigDecimal,
    val creado_en_dispositivo: String,
    val recibido_en: String,
    val nota_supervisor: String? = null,
    val actualizado_en: String,
    val pedido_items: List<OrderItemDto> = emptyList(),
)

/** Row of `pedido_items`. */
@Serializable
data class OrderItemDto(
    val producto_id: String,
    val cantidad: Int,
    @Serializable(with = BigDecimalSerializer::class) val precio_visto_bs: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val precio_aplicado_bs: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val subtotal_bs: BigDecimal,
    val problema: String? = null,
)
