package com.distribuidora.vendedor.core

import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Serializable
    private data class Priced(
        @Serializable(with = BigDecimalSerializer::class) val precio_visto_bs: BigDecimal,
    )

    @Test
    fun `formatBs pads to two decimals`() {
        assertEquals("Bs 16.50", formatBs(BigDecimal("16.5")))
        assertEquals("Bs 0.00", formatBs(BigDecimal.ZERO))
        assertEquals("Bs 1234.00", formatBs(BigDecimal("1234")))
    }

    @Test
    fun `subtotal rounds HALF_UP to two decimals`() {
        assertEquals(BigDecimal("16.67"), subtotal(3, BigDecimal("5.555")))
        assertEquals(BigDecimal("49.50"), subtotal(3, BigDecimal("16.50")))
        assertEquals(BigDecimal("0.00"), subtotal(0, BigDecimal("16.50")))
    }

    @Test
    fun `total sums with scale two`() {
        val sum = total(listOf(BigDecimal("16.50"), BigDecimal("0.1"), BigDecimal("0.2")))
        assertEquals(BigDecimal("16.80"), sum)
        assertEquals(BigDecimal("0.00"), total(emptyList()))
    }

    @Test
    fun `serializer writes an unquoted literal with two decimals`() {
        val json = Json.encodeToString(Priced.serializer(), Priced(BigDecimal("16.5")))
        assertEquals("""{"precio_visto_bs":16.50}""", json)
    }

    @Test
    fun `serializer reads numbers and strings into scale two`() {
        val fromNumber = Json.decodeFromString(Priced.serializer(), """{"precio_visto_bs":16.5}""")
        assertEquals(BigDecimal("16.50"), fromNumber.precio_visto_bs)

        val fromString = Json.decodeFromString(Priced.serializer(), """{"precio_visto_bs":"123.50"}""")
        assertEquals(BigDecimal("123.50"), fromString.precio_visto_bs)
    }

    @Test
    fun `money text round-trips without losing the scale`() {
        assertEquals("7.00", "7".toMoney().toMoneyString())
        assertEquals("7.10", BigDecimal("7.1").toMoneyString())
    }
}
