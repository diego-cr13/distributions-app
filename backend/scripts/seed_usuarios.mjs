// Crea en Supabase Auth los usuarios de vendedores.csv y hace upsert en perfiles.
// Uso: node seed_usuarios.mjs   (lee backend/scripts/.env)
// Usa la service_role key: NUNCA copiar este código a la app ni a la consola.
// Repetible: busca cada usuario antes de crearlo; a los existentes no les cambia la contraseña.

import { fileURLToPath } from 'node:url';
import { createClient } from '@supabase/supabase-js';
import { leerCsv } from './csv.mjs';

try {
  process.loadEnvFile(fileURLToPath(new URL('.env', import.meta.url)));
} catch {
  // Sin .env: se usan las variables del entorno.
}

const { SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
const PASSWORD = process.env.SEED_PASSWORD || 'Distribuidora2026!';

if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
  console.error('Faltan SUPABASE_URL y/o SUPABASE_SERVICE_ROLE_KEY (copiar .env.example a .env).');
  process.exit(1);
}

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const emailDe = (codigo) => `${codigo.toLowerCase()}@distribuidora.test`;

async function usuariosExistentes() {
  const porEmail = new Map();
  for (let page = 1; ; page++) {
    const { data, error } = await supabase.auth.admin.listUsers({ page, perPage: 1000 });
    if (error) throw error;
    for (const u of data.users) porEmail.set(u.email?.toLowerCase(), u);
    if (data.users.length < 1000) return porEmail;
  }
}

const vendedores = leerCsv('vendedores.csv', ['codigo', 'nombre', 'rol']);
for (const v of vendedores) {
  if (!['vendedor', 'supervisor'].includes(v.rol)) {
    throw new Error(`vendedores.csv ${v.codigo}: rol inválido "${v.rol}"`);
  }
}

const existentes = await usuariosExistentes();
let creados = 0;

for (const v of vendedores) {
  const email = emailDe(v.codigo);
  let usuario = existentes.get(email);

  if (!usuario) {
    const { data, error } = await supabase.auth.admin.createUser({
      email,
      password: PASSWORD,
      email_confirm: true,
    });
    if (error) throw new Error(`${v.codigo}: no se pudo crear el usuario: ${error.message}`);
    usuario = data.user;
    creados++;
  }

  const { error } = await supabase.from('perfiles').upsert(
    { id: usuario.id, codigo: v.codigo, nombre: v.nombre, rol: v.rol, activo: true },
    { onConflict: 'codigo' },
  );
  if (error) throw new Error(`${v.codigo}: no se pudo guardar el perfil: ${error.message}`);

  console.log(`${v.codigo.padEnd(5)} ${v.rol.padEnd(10)} ${email}`);
}

console.log(`Listo: ${vendedores.length} perfiles (${creados} usuarios nuevos en Auth).`);
