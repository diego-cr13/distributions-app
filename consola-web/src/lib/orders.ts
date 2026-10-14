// Console-side types and queries for orders. Column names and internal status
// values stay in Spanish because they are the schema already applied in Supabase.

import { supabase } from './supabase'
import type { Json } from './database.types'

export type OrderStatus = 'enviado' | 'confirmado' | 'con_problema' | 'rechazado'
export type ItemProblem = 'sin_stock' | 'precio_cambiado'

export interface OrderItem {
  producto_id: string
  cantidad: number
  precio_visto_bs: number
  precio_aplicado_bs: number | null
  subtotal_bs: number
  problema: ItemProblem | null
  producto: { codigo: string; nombre: string; unidad: string; precio_bs: number; stock: number }
}

export interface Order {
  id: string
  estado: OrderStatus
  motivo: string | null
  total_bs: number
  recibido_en: string // ISO, displayed in La Paz time
  nota_supervisor: string | null
  vendedor: { codigo: string; nombre: string }
  cliente: { codigo: string; nombre: string }
  items: OrderItem[]
}

// Single query shared by "Pedidos del día" and "Problemas". `pedidos` has two
// foreign keys to `perfiles` (vendedor_id, resuelto_por), so the seller embed
// names its constraint explicitly.
const ORDER_SELECT = `
  id, estado, motivo, total_bs, recibido_en, nota_supervisor,
  vendedor:perfiles!pedidos_vendedor_id_fkey(codigo, nombre),
  cliente:clientes(codigo, nombre),
  items:pedido_items(
    producto_id, cantidad, precio_visto_bs, precio_aplicado_bs, subtotal_bs, problema,
    producto:productos(codigo, nombre, unidad, precio_bs, stock)
  )
`

export interface FetchOrdersOptions {
  /** ISO lower bound for `recibido_en` (inclusive). */
  since?: string
  status?: OrderStatus
  /** Sort by `recibido_en`; newest first by default. */
  ascending?: boolean
}

export async function fetchOrders(options: FetchOrdersOptions = {}): Promise<Order[]> {
  let query = supabase
    .from('pedidos')
    .select(ORDER_SELECT)
    .order('recibido_en', { ascending: options.ascending ?? false })
    .order('id', { referencedTable: 'pedido_items', ascending: true })
  if (options.since) query = query.gte('recibido_en', options.since)
  if (options.status) query = query.eq('estado', options.status)

  const { data, error } = await query
  if (error) throw new Error(`No se pudieron cargar los pedidos: ${error.message}`)
  return data as unknown as Order[]
}

export type ResolveAction = 'aprobar' | 'ajustar' | 'rechazar'

export interface AdjustedItem {
  producto_id: string
  cantidad: number
  precio_bs: number
}

/**
 * Calls `resolver_pedido`. The backend raises its errors in Spanish; the message
 * is surfaced as-is so the supervisor sees e.g. "Stock insuficiente: ...".
 */
export async function resolveOrder(
  id: string,
  action: ResolveAction,
  note: string | null,
  adjustedItems?: AdjustedItem[],
): Promise<void> {
  const { error } = await supabase.rpc('resolver_pedido', {
    p_id: id,
    p_accion: action,
    p_nota: note ?? undefined,
    p_items_ajustados: adjustedItems as unknown as Json | undefined,
  })
  if (error) throw new Error(error.message)
}

/** Number of orders waiting for a supervisor decision, for the tab counter. */
export async function countProblemOrders(): Promise<number> {
  const { count, error } = await supabase
    .from('pedidos')
    .select('id', { count: 'exact', head: true })
    .eq('estado', 'con_problema')
  if (error) throw new Error(error.message)
  return count ?? 0
}
