// User-facing labels in Spanish. Internal values stay as stored in the schema.

import type { ItemProblem, OrderStatus } from './orders'

/** Status labels exactly as defined in RF-5. */
export const STATUS_LABELS: Record<OrderStatus, string> = {
  enviado: 'Enviado',
  confirmado: 'Confirmado',
  con_problema: 'Con problema',
  rechazado: 'Rechazado',
}

export const PROBLEM_LABELS: Record<ItemProblem, string> = {
  sin_stock: 'Sin stock',
  precio_cambiado: 'Precio cambiado',
}

export function statusLabel(status: string): string {
  return STATUS_LABELS[status as OrderStatus] ?? status
}

export function problemLabel(problem: string | null): string {
  if (problem === null) return ''
  return PROBLEM_LABELS[problem as ItemProblem] ?? problem
}
