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
4. **App**: copiar `app-android/local.properties.example` a `app-android/local.properties` y completar `sdk.dir` (con barras `/`, por ejemplo `C:/Users/<usuario>/AppData/Local/Android/Sdk`), `SUPABASE_URL` y `SUPABASE_ANON_KEY`. No hace falta Gradle ni Java en el PATH: el wrapper (Gradle 9.6.0) está commiteado y se compila con el JBR de Android Studio (Java 25):
   ```
   export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # PowerShell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   cd app-android
   ./gradlew assembleDebug            # primera vez: descarga las dependencias
   ./gradlew testDebugUnitTest        # tests de dinero y tiempo (app/src/test)
   adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk   # AVD A (V-01)
   adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk   # AVD B (V-02)
   ```
   Se prueba con dos AVD a la vez (compilar e instalar en A antes de levantar B, por RAM). Modo avión por AVD: `adb -s <serial> shell cmd connectivity airplane-mode enable|disable`. Toolchain: Gradle 9.6.0, AGP 9.4.1 (Kotlin integrado), Kotlin 2.2.10, KSP 2.3.6, Room 2.7.x, WorkManager 2.9.x, supabase-kt 3.0.x (ver "Decisiones" en `specs/02-app-android.md`).

## Pendientes

- **Consola web** (`specs/01-consola-web.md`): sin pendientes. Fuera de alcance por decisión: historial de días anteriores, reportes, alta de productos, gestión de clientes/vendedores, versión móvil, tests automatizados y despliegue.
- **App Android** (`specs/02-app-android.md`): fuera de alcance por decisión: editar o borrar pedidos desde la app, alta de clientes, cambio o recuperación de contraseña, Realtime y notificaciones, filtros o reportes en Pedidos, build de release y firma, migraciones de Room (destructiva en el MVP), tests instrumentados o de UI. Pendientes observados: no hay forma de provocar desde la app un rechazo por datos de `enviar_pedido` (la app valida lo mismo que la RPC), así que ese camino (`lastError` en la lista) solo se verificó por lectura del código; el mensaje `motivo` del servidor se muestra tal cual (`sin_stock: P-0004`); al abrir la app en frío el emulador tarda unos segundos en cargar la sesión y mientras tanto la pantalla queda en blanco.
- **Backend**: el advisor de seguridad de Supabase reporta `public.rls_auto_enable()` ejecutable por `anon`; no está en `backend/migrations/`. Revisar su origen en la próxima spec de backend.
