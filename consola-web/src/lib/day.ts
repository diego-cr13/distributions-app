// Business day helpers. "Today's orders" are defined in America/La_Paz,
// a fixed UTC-4 offset with no daylight saving time.

const LA_PAZ_OFFSET_MS = 4 * 60 * 60 * 1000
const LA_PAZ_OFFSET_SUFFIX = '-04:00'

/**
 * ISO timestamp of 00:00 of the current day in La Paz, e.g. "2026-10-13T00:00:00-04:00".
 * Used as the lower bound for `recibido_en >= ...`.
 */
export function startOfDayLaPaz(now: Date = new Date()): string {
  // Shift the instant by -4h; its UTC calendar date is then the La Paz date.
  const shifted = new Date(now.getTime() - LA_PAZ_OFFSET_MS)
  const year = shifted.getUTCFullYear()
  const month = String(shifted.getUTCMonth() + 1).padStart(2, '0')
  const day = String(shifted.getUTCDate()).padStart(2, '0')
  return `${year}-${month}-${day}T00:00:00${LA_PAZ_OFFSET_SUFFIX}`
}

const timeFormatter = new Intl.DateTimeFormat('es-BO', {
  timeZone: 'America/La_Paz',
  hour: '2-digit',
  minute: '2-digit',
})

/** Formats an ISO timestamp as a short La Paz time, e.g. "09:05 a. m." (es-BO locale). */
export function formatTimeLaPaz(iso: string): string {
  return timeFormatter.format(new Date(iso))
}
