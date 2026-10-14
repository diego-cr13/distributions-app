import { useCallback, useEffect, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './lib/supabase'
import { fetchSupervisorProfile, type SupervisorProfile } from './lib/session'
import { countProblemOrders } from './lib/orders'
import Login from './components/Login'
import TodayOrders from './components/TodayOrders'
import Catalog from './components/Catalog'
import ProblemOrders from './components/ProblemOrders'

type View = 'orders' | 'catalog' | 'problems'

const VIEW_LABELS: Record<View, string> = {
  orders: 'Pedidos del día',
  catalog: 'Catálogo',
  problems: 'Problemas',
}

function App() {
  const [session, setSession] = useState<Session | null>(null)
  const [profile, setProfile] = useState<SupervisorProfile | null>(null)
  const [restoring, setRestoring] = useState(true)
  const [view, setView] = useState<View>('orders')
  // Bumped on every Realtime event on `pedidos`; orders and problems refetch when it changes.
  const [ordersVersion, setOrdersVersion] = useState(0)
  const [problemCount, setProblemCount] = useState<number | null>(null)

  const bumpOrdersVersion = useCallback(() => setOrdersVersion((v) => v + 1), [])

  // Restore the persisted session on load and re-validate the supervisor role.
  useEffect(() => {
    let cancelled = false

    async function restore() {
      try {
        const { data } = await supabase.auth.getSession()
        const restored = data.session
        if (!restored) return
        const restoredProfile = await fetchSupervisorProfile(restored.user.id)
        if (cancelled) return
        if (!restoredProfile) {
          await supabase.auth.signOut()
          return
        }
        setSession(restored)
        setProfile(restoredProfile)
      } catch {
        await supabase.auth.signOut()
      } finally {
        if (!cancelled) setRestoring(false)
      }
    }

    void restore()

    // Login.tsx handles SIGNED_IN itself (it validates the role before reporting
    // the session), so here we only react to the session going away.
    const { data: subscription } = supabase.auth.onAuthStateChange((event) => {
      if (event === 'SIGNED_OUT') {
        setSession(null)
        setProfile(null)
      }
    })

    return () => {
      cancelled = true
      subscription.subscription.unsubscribe()
    }
  }, [])

  // Single Realtime channel for the whole console, alive while there is a session.
  const userId = session?.user.id
  useEffect(() => {
    if (!userId) return
    const channel = supabase
      .channel('pedidos-changes')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'pedidos' }, bumpOrdersVersion)
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'pedidos' }, bumpOrdersVersion)
      .subscribe()
    return () => {
      void supabase.removeChannel(channel)
    }
  }, [userId, bumpOrdersVersion])

  // Counter shown on the "Problemas" tab, refreshed with every orders change.
  useEffect(() => {
    if (!userId) return
    let cancelled = false
    countProblemOrders()
      .then((count) => {
        if (!cancelled) setProblemCount(count)
      })
      .catch(() => {
        if (!cancelled) setProblemCount(null)
      })
    return () => {
      cancelled = true
    }
  }, [userId, ordersVersion])

  async function handleLogout() {
    await supabase.auth.signOut()
    setSession(null)
    setProfile(null)
    setView('orders')
  }

  if (restoring) {
    return <p className="loading">Cargando…</p>
  }

  if (!session || !profile) {
    return (
      <Login
        onLogin={(newSession, newProfile) => {
          setSession(newSession)
          setProfile(newProfile)
        }}
      />
    )
  }

  return (
    <>
      <header className="app-header">
        <h1>Consola de supervisores</h1>
        <div className="user">
          <span>
            {profile.codigo} · {profile.nombre}
          </span>
          <button type="button" onClick={handleLogout}>
            Salir
          </button>
        </div>
      </header>
      <nav className="tabs" aria-label="Vistas">
        {(Object.keys(VIEW_LABELS) as View[]).map((key) => (
          <button
            key={key}
            type="button"
            className={key === view ? 'tab active' : 'tab'}
            aria-current={key === view ? 'page' : undefined}
            onClick={() => setView(key)}
          >
            {VIEW_LABELS[key]}
            {key === 'problems' && problemCount !== null && problemCount > 0 && (
              <span className="counter">{problemCount}</span>
            )}
          </button>
        ))}
      </nav>
      <main>
        {view === 'orders' && <TodayOrders ordersVersion={ordersVersion} />}
        {view === 'catalog' && <Catalog />}
        {view === 'problems' && (
          <ProblemOrders ordersVersion={ordersVersion} onResolved={bumpOrdersVersion} />
        )}
      </main>
    </>
  )
}

export default App
