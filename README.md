# Distribuidora de abarrotes – MVP

Reemplaza el proceso de pedidos en papel con:

- **Backend** Supabase en la nube (Postgres, Auth, Realtime, RPC): fuente de verdad de stock y precios.
- **Consola web** (`consola-web/`) para supervisores: Vite + React + TypeScript + `supabase-js`.
- **App Android** (`app-android/`) para vendedores: Kotlin + Compose + Room + WorkManager + `supabase-kt`, con modo offline.

Requerimientos completos: [`docs/requirements.md`](docs/requirements.md).

## Estructura

```
backend/
  migrations/          0001_schema.sql, 0002_rls.sql, 0003_rpc.sql
  seed.sql             productos y clientes (generado con scripts/gen_seed.mjs)
  datos_semilla/       CSV de vendedores, clientes y productos
  scripts/             generación de seed y alta de usuarios (service_role)
consola-web/           consola de supervisores
app-android/           app de vendedores
docs/                  requerimientos
```

## Configuración

Los archivos con secretos no se commitean; copiar cada `.example` y completar:

| Archivo | Variables |
|---------|-----------|
| `backend/scripts/.env` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SEED_PASSWORD` |
| `consola-web/.env.local` | `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` |
| `app-android/local.properties` | `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` |

La app y la consola usan solo la **anon key**. La **service_role key** se usa únicamente en `backend/scripts/`.

## Puesta en marcha

1. **Esquema**: aplicar en orden `0001_schema.sql`, `0002_rls.sql`, `0003_rpc.sql` (se hizo con el MCP de Supabase; también se pueden pegar en el SQL Editor del dashboard).
2. **Seed**:
   ```
   cd backend/scripts
   npm install
   node gen_seed.mjs          # genera backend/seed.sql → aplicarlo (MCP o SQL Editor)
   node seed_usuarios.mjs     # crea los 11 usuarios (V-01..V-10, S-01)
   ```
   Login: código del vendedor (`V-01` → `v-01@distribuidora.test`), contraseña `SEED_PASSWORD`.
3. **Consola**: `cd consola-web && npm install && npm run dev`.
4. **App**: con `JAVA_HOME` apuntando al JBR de Android Studio:
   ```
   cd app-android
   gradlew assembleDebug
   adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Pendientes

- **Consola web** (`specs/01-consola-web.md`): sin pendientes. Fuera de alcance por decisión: historial de días anteriores, reportes, alta de productos, gestión de clientes/vendedores, versión móvil, tests automatizados y despliegue.
- **App Android**: por construir.
- **Backend**: el advisor de seguridad de Supabase reporta `public.rls_auto_enable()` ejecutable por `anon`; no está en `backend/migrations/`. Revisar su origen en la próxima spec de backend.
