// Money helpers. Amounts arrive from PostgREST as JSON numbers with 2 decimals
// (numeric(10,2) / numeric(12,2)). They are converted ONCE to integer cents and
// every sum in the console is done on integers; floats are never added.

/** Converts a 2-decimal amount (e.g. 16.5) to integer cents (1650). */
export function toCents(amount: number): number {
  return Math.round(amount * 100)
}

/** Formats integer cents as "Bs 16.50". */
export function formatBs(cents: number): string {
  const sign = cents < 0 ? '-' : ''
  const abs = Math.abs(cents)
  const whole = Math.floor(abs / 100)
  const fraction = abs % 100
  return `${sign}Bs ${whole}.${fraction.toString().padStart(2, '0')}`
}

const PRICE_PATTERN = /^\d+(\.\d{1,2})?$/

/** True when the text is a non-negative number with at most 2 decimals. */
export function isValidPrice(text: string): boolean {
  return PRICE_PATTERN.test(text.trim())
}

/** Parses a validated price text ("16.5") to integer cents (1650). */
export function parsePriceToCents(text: string): number {
  const [whole, fraction = ''] = text.trim().split('.')
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}

/** Converts integer cents back to the 2-decimal number sent to the backend (1650 → 16.5). */
export function centsToAmount(cents: number): number {
  return Number((cents / 100).toFixed(2))
}
