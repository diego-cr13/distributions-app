package com.distribuidora.vendedor.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Time helpers. Every timestamp stored in Room is UTC in `Instant.toString()` form
 * (e.g. `2026-10-14T13:15:00Z`), whether it came from the phone or from PostgREST
 * (which returns `+00:00`). Sorting by that text is then chronologically correct.
 * Conversion to La Paz happens only when displaying.
 */
val LA_PAZ_ZONE: ZoneId = ZoneId.of("America/La_Paz")

private val LA_PAZ_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(LA_PAZ_ZONE)

/** Current time as UTC ISO-8601 text; used for `creado_en_dispositivo`. */
fun nowUtc(): String = Instant.now().toString()

/**
 * Normalizes any ISO-8601 timestamp with offset (`-04:00`, `+00:00`, `Z`) to UTC
 * `Instant.toString()` form. A timestamp without offset is taken as UTC.
 */
fun toUtc(iso: String): String = parseInstant(iso).toString()

/** `dd/MM HH:mm` in America/La_Paz for lists and detail screens. */
fun formatLaPaz(iso: String): String = LA_PAZ_FORMAT.format(parseInstant(iso))

private fun parseInstant(iso: String): Instant =
    try {
        OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (e: DateTimeParseException) {
        LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC)
    }
