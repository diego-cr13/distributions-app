import { useCallback, useEffect, useState } from 'react'
import { supabase } from '../lib/supabase'
import type { Tables } from '../lib/database.types'
import { centsToAmount, formatBs, isValidPrice, parsePriceToCents, toCents } from '../lib/money'

type Product = Tables<'productos'>

interface EditState {
  id: string
  priceText: string
  stockText: string
  error: string | null
  saving: boolean
}

const STOCK_PATTERN = /^\d+$/

/** Returns a Spanish message when the inputs are invalid, or null when they can be sent. */
function validate(priceText: string, stockText: string): string | null {
  if (!isValidPrice(priceText)) return 'El precio debe ser un número con hasta 2 decimales, por ejemplo 16.50'
  if (parsePriceToCents(priceText) <= 0) return 'El precio debe ser mayor que 0'
  if (!STOCK_PATTERN.test(stockText.trim())) return 'El stock debe ser un número entero mayor o igual a 0'
  return null
}

export default function Catalog() {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [edit, setEdit] = useState<EditState | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    const { data, error: queryError } = await supabase.from('productos').select('*').order('codigo')
    if (queryError) {
      setError(`No se pudo cargar el catálogo: ${queryError.message}`)
    } else {
      setProducts(data)
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  function startEdit(product: Product) {
    setEdit({
      id: product.id,
      priceText: product.precio_bs.toFixed(2),
      stockText: String(product.stock),
      error: null,
      saving: false,
    })
  }

  async function save() {
    if (!edit) return
    const validationError = validate(edit.priceText, edit.stockText)
    if (validationError) {
      setEdit({ ...edit, error: validationError })
      return
    }
    setEdit({ ...edit, error: null, saving: true })

    const { data, error: updateError } = await supabase
      .from('productos')
      .update({
        precio_bs: centsToAmount(parsePriceToCents(edit.priceText)),
        stock: Number(edit.stockText.trim()),
      })
      .eq('id', edit.id)
      .select()
      .single()

    if (updateError) {
      setEdit({ ...edit, error: `No se pudo guardar: ${updateError.message}`, saving: false })
      return
    }
    setProducts((current) => current.map((p) => (p.id === data.id ? data : p)))
    setEdit(null)
  }

  return (
    <section>
      <div className="toolbar">
        <h2>Catálogo</h2>
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
              <th>Código</th>
              <th>Nombre</th>
              <th>Unidad</th>
              <th className="money">Precio</th>
              <th className="number">Stock</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => {
              const editing = edit?.id === product.id
              return (
                <tr key={product.id} className={editing ? 'editing' : undefined}>
                  <td>{product.codigo}</td>
                  <td>{product.nombre}</td>
                  <td>{product.unidad}</td>
                  <td className="money">
                    {editing && edit ? (
                      <input
                        type="text"
                        inputMode="decimal"
                        className="short"
                        value={edit.priceText}
                        onChange={(e) => setEdit({ ...edit, priceText: e.target.value, error: null })}
                        aria-label="Precio en bolivianos"
                        autoFocus
                      />
                    ) : (
                      formatBs(toCents(product.precio_bs))
                    )}
                  </td>
                  <td className="number">
                    {editing && edit ? (
                      <input
                        type="text"
                        inputMode="numeric"
                        className="short"
                        value={edit.stockText}
                        onChange={(e) => setEdit({ ...edit, stockText: e.target.value, error: null })}
                        aria-label="Stock"
                      />
                    ) : (
                      product.stock
                    )}
                  </td>
                  <td className="actions">
                    {editing && edit ? (
                      <>
                        <button type="button" className="primary" onClick={() => void save()} disabled={edit.saving}>
                          {edit.saving ? 'Guardando…' : 'Guardar'}
                        </button>
                        <button type="button" onClick={() => setEdit(null)} disabled={edit.saving}>
                          Cancelar
                        </button>
                        {edit.error && (
                          <p className="error" role="alert">
                            {edit.error}
                          </p>
                        )}
                      </>
                    ) : (
                      <button type="button" onClick={() => startEdit(product)} disabled={edit !== null}>
                        Editar
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
            {products.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  {loading ? 'Cargando catálogo…' : 'No hay productos.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}
