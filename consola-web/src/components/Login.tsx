import { useState, type FormEvent } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from '../lib/supabase'
import {
  NOT_SUPERVISOR_MESSAGE,
  authErrorMessage,
  emailFromCode,
  fetchSupervisorProfile,
  type SupervisorProfile,
} from '../lib/session'

interface Props {
  onLogin: (session: Session, profile: SupervisorProfile) => void
}

export default function Login({ onLogin }: Props) {
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const { data, error: authError } = await supabase.auth.signInWithPassword({
        email: emailFromCode(code),
        password,
      })
      if (authError || !data.session) {
        setError(authError ? authErrorMessage(authError) : 'No se pudo iniciar sesión')
        return
      }
      const profile = await fetchSupervisorProfile(data.session.user.id)
      if (!profile) {
        await supabase.auth.signOut()
        setError(NOT_SUPERVISOR_MESSAGE)
        return
      }
      onLogin(data.session, profile)
    } catch (err) {
      await supabase.auth.signOut()
      setError(err instanceof Error ? err.message : 'Error inesperado al iniciar sesión')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login">
      <form className="card login-form" onSubmit={handleSubmit}>
        <h1>Consola de supervisores</h1>
        <label>
          Código
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="S-01"
            autoComplete="username"
            autoFocus
            required
          />
        </label>
        <label>
          Contraseña
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </main>
  )
}
