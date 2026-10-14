import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchOrders, type Order } from '../lib/orders'
import { formatBs, toCents } from '../lib/money'
import { formatTimeLaPaz, startOfDayLaPaz } from '../lib/day'
import { statusLabel } from '../lib/labels'

interface Props {
  ordersVersion: number
}

type GroupBy = 'none' | 'vendor' | 'client'

interface Party {
  codigo: string
  nombre: string
}

interface Group {
  key: string
  label: string
  orders: Order[]
}

function partyLabel(party: Party): string {
  return `${party.codigo} · ${party.nombre}`
}

function uniqueParties(orders: Order[], pick: (order: Order) => Party): Party[] {
  const byCode = new Map<string, Party>()
  for (const order of orders) {
    const party = pick(order)
    if (!byCode.has(party.codigo)) byCode.set(party.codigo, party)
  }
  return [...byCode.values()].sort((a, b) => a.codigo.localeCompare(b.codigo))
}

/** Sum in integer cents of the confirmed orders only (rejected and observed orders are not sales). */
function confirmedTotalCents(orders: Order[]): number {
  let cents = 0
  for (const order of orders) {
    if (order.estado === 'confirmado') cents += toCents(order.total_bs)
  }
  return cents
}

function countConfirmed(orders: Order[]): number {
  return orders.filter((order) => order.estado === 'confirmado').length
}

export default function TodayOrders({ ordersVersion }: Props) {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [vendorFilter, setVendorFilter] = useState('')
  const [clientFilter, setClientFilter] = useState('')
  const [groupBy, setGroupBy] = useState<GroupBy>('none')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Recomputed on every fetch so the list rolls over at midnight in La Paz.
      setOrders(await fetchOrders({ since: startOfDayLaPaz() }))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar los pedidos')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, ordersVersion])

  const vendors = useMemo(() => uniqueParties(orders, (o) => o.vendedor), [orders])
  const clients = useMemo(() => uniqueParties(orders, (o) => o.cliente), [orders])

  const filtered = useMemo(
    () =>
      orders.filter(
        (order) =>
          (vendorFilter === '' || order.vendedor.codigo === vendorFilter) &&
          (clientFilter === '' || order.cliente.codigo === clientFilter),
      ),
    [orders, vendorFilter, clientFilter],
  )

  const groups = useMemo<Group[]>(() => {
    if (groupBy === 'none') return [{ key: 'all', label: '', orders: filtered }]
    const pick = groupBy === 'vendor' ? (o: Order) => o.vendedor : (o: Order) => o.cliente
    const byKey = new Map<string, Group>()
    for (const order of filtered) {
      const party = pick(order)
      let group = byKey.get(party.codigo)
      if (!group) {
        group = { key: party.codigo, label: partyLabel(party), orders: [] }
        byKey.set(party.codigo, group)
      }
      group.orders.push(order)
    }
    return [...byKey.values()].sort((a, b) => a.key.localeCompare(b.key))
  }, [filtered, groupBy])

  // Day totals are over every order of the day, regardless of the filters.
  const dayTotalCents = confirmedTotalCents(orders)
  const dayConfirmedCount = countConfirmed(orders)
  const vendorBreakdown = useMemo(
    () =>
      vendors.map((vendor) => {
        const own = orders.filter((o) => o.vendedor.codigo === vendor.codigo)
        return { vendor, count: countConfirmed(own), cents: confirmedTotalCents(own) }
      }),
    [orders, vendors],
  )

  const rows: ReactNode[] = []
  for (const group of groups) {
    if (groupBy !== 'none') {
      rows.push(
        <tr key={`group-${group.key}`} className="group-row">
          <th colSpan={4}>
            {group.label} · {group.orders.length} {group.orders.length === 1 ? 'pedido' : 'pedidos'}
          </th>
          <th className="money">{formatBs(confirmedTotalCents(group.orders))}</th>
          <th>Subtotal confirmado</th>
        </tr>,
      )
    }
    for (const order of group.orders) {
      rows.push(
        <tr key={order.id}>
          <td>{formatTimeLaPaz(order.recibido_en)}</td>
          <td>{partyLabel(order.vendedor)}</td>
          <td>{partyLabel(order.cliente)}</td>
          <td>
            <ul className="items">
              {order.items.map((item) => (
                <li key={item.producto_id}>
                  {item.cantidad} × {item.producto.nombre}
                </li>
              ))}
            </ul>
          </td>
          <td className="money">{formatBs(toCents(order.total_bs))}</td>
          <td>
            <span className={`badge badge-${order.estado}`}>{statusLabel(order.estado)}</span>
          </td>
        </tr>,
      )
    }
  }

  return (
    <section>
      <div className="toolbar">
        <h2>Pedidos del día</h2>
        <label>
          Vendedor
          <select value={vendorFilter} onChange={(e) => setVendorFilter(e.target.value)}>
            <option value="">Todos</option>
            {vendors.map((v) => (
              <option key={v.codigo} value={v.codigo}>
                {partyLabel(v)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Cliente
          <select value={clientFilter} onChange={(e) => setClientFilter(e.target.value)}>
            <option value="">Todos</option>
            {clients.map((c) => (
              <option key={c.codigo} value={c.codigo}>
                {partyLabel(c)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Agrupar por
          <select value={groupBy} onChange={(e) => setGroupBy(e.target.value as GroupBy)}>
            <option value="none">Ninguno</option>
            <option value="vendor">Vendedor</option>
            <option value="client">Cliente</option>
          </select>
        </label>
        <button type="button" onClick={() => void load()} disabled={loading}>
          {loading ? 'Actualizando…' : 'Actualizar'}
        </button>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>Hora</th>
              <th>Vendedor</th>
              <th>Cliente</th>
              <th>Ítems</th>
              <th className="money">Total</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {rows.length > 0 ? (
              rows
            ) : (
              <tr>
                <td colSpan={6} className="empty">
                  {loading
                    ? 'Cargando pedidos…'
                    : orders.length === 0
                      ? 'Todavía no hay pedidos hoy.'
                      : 'Ningún pedido coincide con los filtros.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="card totals">
        <h3>Total del día</h3>
        <p>
          <strong>{dayConfirmedCount}</strong> {dayConfirmedCount === 1 ? 'pedido confirmado' : 'pedidos confirmados'}{' '}
          · <strong>{formatBs(dayTotalCents)}</strong>
        </p>
        <p className="hint">Solo se suman los pedidos confirmados; los rechazados y con problema no cuentan.</p>
        <table className="compact">
          <thead>
            <tr>
              <th>Vendedor</th>
              <th>Confirmados</th>
              <th className="money">Monto</th>
            </tr>
          </thead>
          <tbody>
            {vendorBreakdown.map(({ vendor, count, cents }) => (
              <tr key={vendor.codigo}>
                <td>{partyLabel(vendor)}</td>
                <td>{count}</td>
                <td className="money">{formatBs(cents)}</td>
              </tr>
            ))}
            {vendorBreakdown.length === 0 && (
              <tr>
                <td colSpan={3} className="empty">
                  Sin vendedores con pedidos hoy.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}
