package com.distribuidora.vendedor.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/**
 * Money helpers. Every amount in memory is a [BigDecimal] with scale 2 and
 * HALF_UP rounding; Room and the DTOs carry it as text. Never a Double.
 */
const val MONEY_SCALE = 2
val MONEY_ROUNDING: RoundingMode = RoundingMode.HALF_UP

/** Normalizes any amount to scale 2, HALF_UP. */
fun BigDecimal.toMoney(): BigDecimal = setScale(MONEY_SCALE, MONEY_ROUNDING)

/** Parses an amount stored as text (Room, DTOs) into a scale-2 [BigDecimal]. */
fun String.toMoney(): BigDecimal = BigDecimal(this).toMoney()

/** Plain text form with exactly 2 decimals, e.g. "16.50", for Room and DTOs. */
fun BigDecimal.toMoneyString(): String = toMoney().toPlainString()

/** User-facing format: `Bs 16.50`. */
fun formatBs(value: BigDecimal): String = "Bs ${value.toMoneyString()}"

/** quantity × price, HALF_UP to 2 decimals. */
fun subtotal(quantity: Int, price: BigDecimal): BigDecimal =
    price.multiply(BigDecimal.valueOf(quantity.toLong())).toMoney()

/** Sum of amounts, scale 2. */
fun total(items: Iterable<BigDecimal>): BigDecimal =
    items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item) }.toMoney()

/**
 * JSON (de)serializer for money.
 *
 * Reading accepts the JSON number as-is (`16.5`, `123.50`, or a quoted string) and
 * normalizes it to scale 2. Writing emits an unquoted numeric literal with exactly
 * 2 decimals (`16.50`), so `precio_visto_bs` reaches the RPC as a `numeric` value
 * with the same digits the vendor saw in the local catalog.
 */
@OptIn(ExperimentalSerializationApi::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.distribuidora.vendedor.core.Money", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeString().toMoney()
        return jsonDecoder.decodeJsonElement().jsonPrimitive.content.toMoney()
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        val literal = value.toMoneyString()
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder != null) {
            jsonEncoder.encodeJsonElement(JsonUnquotedLiteral(literal))
        } else {
            encoder.encodeString(literal)
        }
    }
}
