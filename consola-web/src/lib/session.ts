import { supabase } from './supabase'

export interface SupervisorProfile {
  codigo: string
  nombre: string
}

export const NOT_SUPERVISOR_MESSAGE = 'Solo un supervisor puede entrar a la consola'

const AUTH_ERROR_MESSAGES: Record<string, string> = {
  invalid_credentials: 'Código o contraseña incorrectos',
  email_not_confirmed: 'La cuenta no está confirmada',
  over_request_rate_limit: 'Demasiados intentos. Espera un momento y vuelve a intentar',
}

/** Login code (S-01) → seed email (s-01@distribuidora.test). */
export function emailFromCode(code: string): string {
  return `${code.trim().toLowerCase()}@distribuidora.test`
}

/** Maps a supabase-js auth error to a Spanish message. */
export function authErrorMessage(error: { code?: string; message: string }): string {
  return (error.code && AUTH_ERROR_MESSAGES[error.code]) ?? `No se pudo iniciar sesión: ${error.message}`
}

/**
 * Reads the signed-in user's own `perfiles` row and returns it only when it is
 * an active supervisor. RLS hides inactive profiles, so "not found" also means
 * "not allowed". Returns null when the user must not use the console.
 */
export async function fetchSupervisorProfile(userId: string): Promise<SupervisorProfile | null> {
  const { data, error } = await supabase
    .from('perfiles')
    .select('codigo, nombre, rol, activo')
    .eq('id', userId)
    .maybeSingle()
  if (error) throw new Error(`No se pudo leer el perfil: ${error.message}`)
  if (!data || data.rol !== 'supervisor' || data.activo === false) return null
  return { codigo: data.codigo, nombre: data.nombre }
}
