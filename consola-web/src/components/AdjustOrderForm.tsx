import { useMemo, useState, type FormEvent } from 'react'
import { resolveOrder, type AdjustedItem, type Order } from '../lib/orders'
import { centsToAmount, formatBs, isValidPrice, parsePriceToCents, toCents } from '../lib/money'

interface Props {
  order: Order
  /** Optional supervisor note typed in the order's note field. */
  note: string | null
  onConfirmed: () => void
  onCancel: () => void
}

interface Row {
  producto_id: string
  codigo: string
  nombre: string
  stock: number
  originalQuantity: number
  seenPriceCents: number
  quantityText: string
  priceText: string
}

const QUANTITY_PATTERN = /^\d+$/

function rowQuantity(row: Row): number | null {
  return QUANTITY_PATTERN.test(row.quantityText.trim()) ? Number(row.quantityText.trim()) : null
}

function rowPriceCents(row: Row): number | null {
  return isValidPrice(row.priceText) ? parsePriceToCents(row.priceText) : null
}

/** Message for an invalid row, or null when it can be sent. */
function rowError(row: Row): string | null {
  const quantity = rowQuantity(row)
  if (quantity === null) return 'La cantidad debe ser un número entero mayor o igual a 0'
  if (quantity === 0) return null
  const cents = rowPriceCents(row)
  if (cents === null) return 'El precio debe tener hasta 2 decimales'
  if (cents <= 0) return 'El precio debe ser mayor que 0'
  return null
}

export default function AdjustOrderForm({ order, note, onConfirmed, onCancel }: Props) {
  const [rows, setRows] = useState<Row[]>(() =>
    order.items.map((item) => ({
      producto_id: item.producto_id,
      codigo: item.producto.codigo,
      nombre: item.producto.nombre,
      stock: item.producto.stock,
      originalQuantity: item.cantidad,
      seenPriceCents: toCents(item.precio_visto_bs),
      quantityText: String(item.cantidad),
      // "Ajustar" is the way to apply the current price (§7), so it is preloaded.
      priceText: item.producto.precio_bs.toFixed(2),
    })),
  )
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  function updateRow(index: number, patch: Partial<Row>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)))
    setError(null)
  }

  // Live total in integer cents over the rows that are valid and kept.
  const totalCents = useMemo(() => {
    let cents = 0
    for (const row of rows) {
      const quantity = rowQuantity(row)
      const price = rowPriceCents(row)
      if (quantity && price) cents += quantity * price
    }
    return cents
  }, [rows])

  const rowErrors = rows.map(rowError)
  const allZero = rows.every((row) => rowQuantity(row) === 0)
  const blocked = rowErrors.some((e) => e !== null) || allZero

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (blocked) {
      setError(allZero ? 'Todos los ítems quedaron en 0. Usa Rechazar' : 'Corrige los ítems marcados antes de confirmar')
      return
    }
    const items: AdjustedItem[] = rows.map((row) => ({
      producto_id: row.producto_id,
      cantidad: rowQuantity(row) ?? 0,
      // A removed item (cantidad 0) still needs a valid price for the backend check.
      precio_bs: centsToAmount(rowPriceCents(row) ?? row.seenPriceCents),
    }))
    setSaving(true)
    setError(null)
    try {
      await resolveOrder(order.id, 'ajustar', note, items)
      onConfirmed()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ajustar el pedido')
      setSaving(false)
    }
  }

  return (
    <form className="adjust-form" onSubmit={handleSubmit}>
      <h4>Ajustar pedido</h4>
      <p className="hint">
        El precio viene precargado con el precio vigente. Una cantidad 0 elimina el ítem del pedido.
      </p>
      <table className="compact">
        <thead>
          <tr>
            <th>Producto</th>
            <th className="number">Pedido</th>
            <th className="number">Stock</th>
            <th className="number">Cantidad</th>
            <th className="money">Precio visto</th>
            <th className="money">Precio a aplicar</th>
            <th className="money">Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const quantity = rowQuantity(row)
            const price = rowPriceCents(row)
            const removed = quantity === 0
            const rowProblem = rowErrors[index]
            return (
              <tr key={row.producto_id} className={removed ? 'removed' : rowProblem ? 'invalid' : undefined}>
                <td>
                  {row.codigo} · {row.nombre}
                  {rowProblem && <div className="error small">{rowProblem}</div>}
                </td>
                <td className="number">{row.originalQuantity}</td>
                <td className="number">{row.stock}</td>
                <td className="number">
                  <input
                    type="text"
                    inputMode="numeric"
                    className="short"
                    value={row.quantityText}
                    onChange={(e) => updateRow(index, { quantityText: e.target.value })}
                    aria-label={`Cantidad de ${row.nombre}`}
                    disabled={saving}
                  />
                </td>
                <td className="money">{formatBs(row.seenPriceCents)}</td>
                <td className="money">
                  <input
                    type="text"
                    inputMode="decimal"
                    className="short"
                    value={row.priceText}
                    onChange={(e) => updateRow(index, { priceText: e.target.value })}
                    aria-label={`Precio de ${row.nombre}`}
                    disabled={saving || removed}
                  />
                </td>
                <td className="money">{removed ? 'Eliminado' : quantity && price ? formatBs(quantity * price) : '—'}</td>
              </tr>
            )
          })}
        </tbody>
        <tfoot>
          <tr>
            <th colSpan={6}>Total previsto</th>
            <th className="money">{formatBs(totalCents)}</th>
          </tr>
        </tfoot>
      </table>

      {allZero && <p className="error">Todos los ítems quedaron en 0. Usa Rechazar</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <div className="buttons">
        <button type="submit" className="primary" disabled={saving || blocked}>
          {saving ? 'Confirmando…' : 'Confirmar ajuste'}
        </button>
        <button type="button" onClick={onCancel} disabled={saving}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
