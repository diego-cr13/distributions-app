import { useCallback, useEffect, useState } from 'react'
import { fetchOrders, resolveOrder, type Order } from '../lib/orders'
import { formatBs, toCents } from '../lib/money'
import { formatTimeLaPaz } from '../lib/day'
import { problemLabel } from '../lib/labels'
import AdjustOrderForm from './AdjustOrderForm'

interface Props {
  ordersVersion: number
  /** Called after a successful resolution so the rest of the console refreshes. */
  onResolved: () => void
}

const dateFormatter = new Intl.DateTimeFormat('es-BO', {
  timeZone: 'America/La_Paz',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

export default function ProblemOrders({ ordersVersion, onResolved }: Props) {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notes, setNotes] = useState<Record<string, string>>({})
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({})
  const [busyId, setBusyId] = useState<string | null>(null)
  const [adjustingId, setAdjustingId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Every observed order, whatever its date: yesterday's order still needs a decision.
      setOrders(await fetchOrders({ status: 'con_problema', ascending: true }))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar los pedidos')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, ordersVersion])

  function setRowError(id: string, message: string | null) {
    setRowErrors((current) => {
      const next = { ...current }
      if (message === null) delete next[id]
      else next[id] = message
      return next
    })
  }

  function noteFor(id: string): string | null {
    const text = (notes[id] ?? '').trim()
    return text === '' ? null : text
  }

  async function approve(order: Order) {
    setRowError(order.id, null)
    setBusyId(order.id)
    try {
      await resolveOrder(order.id, 'aprobar', noteFor(order.id))
      onResolved()
    } catch (err) {
      setRowError(order.id, err instanceof Error ? err.message : 'No se pudo aprobar el pedido')
    } finally {
      setBusyId(null)
      // Refetch either way: on error the stock/price shown may have changed.
      void load()
    }
  }

  async function reject(order: Order) {
    const note = noteFor(order.id)
    if (note === null) {
      setRowError(order.id, 'Escribe una nota con el motivo para rechazar el pedido')
      return
    }
    setRowError(order.id, null)
    setBusyId(order.id)
    try {
      await resolveOrder(order.id, 'rechazar', note)
      onResolved()
    } catch (err) {
      setRowError(order.id, err instanceof Error ? err.message : 'No se pudo rechazar el pedido')
    } finally {
      setBusyId(null)
      void load()
    }
  }

  return (
    <section>
      <div className="toolbar">
        <h2>Pedidos con problema</h2>
        <button type="button" onClick={() => void load()} disabled={loading}>
          {loading ? 'Actualizando…' : 'Actualizar'}
        </button>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {orders.length === 0 && (
        <p className="card empty">{loading ? 'Cargando pedidos…' : 'No hay pedidos con problema.'}</p>
      )}

      {orders.map((order) => {
        const busy = busyId === order.id
        const adjusting = adjustingId === order.id
        const received = new Date(order.recibido_en)
        return (
          <article key={order.id} className="card problem-order">
            <header className="problem-header">
              <div>
                <strong>
                  {order.vendedor.codigo} · {order.vendedor.nombre}
                </strong>{' '}
                → {order.cliente.codigo} · {order.cliente.nombre}
                <div className="hint">
                  Recibido el {dateFormatter.format(received)} a las {formatTimeLaPaz(order.recibido_en)} · Total enviado{' '}
                  {formatBs(toCents(order.total_bs))}
                </div>
              </div>
              <span className="badge badge-con_problema">{order.motivo ?? 'Con problema'}</span>
            </header>

            <table className="compact">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th className="number">Cantidad</th>
                  <th className="money">Precio visto</th>
                  <th className="money">Precio vigente</th>
                  <th className="number">Stock actual</th>
                  <th>Problema</th>
                </tr>
              </thead>
              <tbody>
                {order.items.map((item) => (
                  <tr key={item.producto_id} className={item.problema ? 'has-problem' : undefined}>
                    <td>
                      {item.producto.codigo} · {item.producto.nombre}
                    </td>
                    <td className="number">{item.cantidad}</td>
                    <td className="money">{formatBs(toCents(item.precio_visto_bs))}</td>
                    <td className="money">{formatBs(toCents(item.producto.precio_bs))}</td>
                    <td className="number">{item.producto.stock}</td>
                    <td>
                      {item.problema && (
                        <span className={`badge badge-problem-${item.problema}`}>{problemLabel(item.problema)}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {adjusting ? (
              <AdjustOrderForm
                order={order}
                note={noteFor(order.id)}
                onConfirmed={() => {
                  setAdjustingId(null)
                  onResolved()
                  void load()
                }}
                onCancel={() => setAdjustingId(null)}
              />
            ) : (
              <div className="problem-actions">
                <label className="grow">
                  Nota para el vendedor (obligatoria para rechazar)
                  <input
                    type="text"
                    value={notes[order.id] ?? ''}
                    onChange={(e) => setNotes({ ...notes, [order.id]: e.target.value })}
                    placeholder="Ej.: se entrega con el precio nuevo"
                    disabled={busy}
                  />
                </label>
                <div className="buttons">
                  <button type="button" className="primary" onClick={() => void approve(order)} disabled={busy}>
                    {busy ? 'Procesando…' : 'Aprobar'}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setRowError(order.id, null)
                      setAdjustingId(order.id)
                    }}
                    disabled={busy}
                  >
                    Ajustar
                  </button>
                  <button type="button" className="danger" onClick={() => void reject(order)} disabled={busy}>
                    Rechazar
                  </button>
                </div>
              </div>
            )}

            {rowErrors[order.id] && (
              <p className="error" role="alert">
                {rowErrors[order.id]}
              </p>
            )}
          </article>
        )
      })}
    </section>
  )
}
