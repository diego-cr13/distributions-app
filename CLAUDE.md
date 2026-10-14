# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

MVP de una distribuidora de abarrotes que reemplaza pedidos en papel. Tres piezas sobre un mismo proyecto Supabase **en la nube** (ref `woqrkiceajvtptfcvdqt`, MCP configurado en `.mcp.json`):

- `backend/` — esquema Postgres, RLS, RPC y seed. **Fuente de verdad** de stock y precios.
- `consola-web/` — supervisores: Vite + React + TypeScript + `supabase-js`, sin router (estado `view` en `App.tsx`), CSS plano. Implementada según `specs/01-consola-web.md` (vistas `TodayOrders`, `Catalog`, `ProblemOrders` + `AdjustOrderForm`; helpers en `src/lib/`).
- `app-android/` — vendedores: Kotlin + Compose + Room + WorkManager + `supabase-kt` 3.x, offline-first, sin Hilt (singleton `Graph` en `App.kt`). Implementada según `specs/02-app-android.md` (código en inglés: `core/` helpers, `data/local` Room, `data/remote` DTOs, `data/repository` Auth/Order/Sync, `sync/` worker y scheduler, `ui/` pantallas por pestaña). Toolchain Gradle 9.6.0 + AGP 9.4.1 (Kotlin integrado, sin `org.jetbrains.kotlin.android`) + Kotlin 2.2.10 + KSP 2.3.6, porque el JBR de Android Studio es Java 25 y Gradle 8.9 no corre sobre él.

`docs/requirements.md` es la especificación completa y el plan de implementación (secciones 5 y 8 tienen el detalle de tablas, RPC, RLS, consola y app). Leerlo antes de desarrollar cualquier parte. Orden de trabajo acordado: backend → consola → app; lo que no se alcance se anota en "Pendientes" de `README.md`.

## Convenciones obligatorias

- **Idiomas**: las respuestas en el chat van en español. El código nuevo va en inglés (variables, funciones, tipos, comentarios y mensajes de commit). Todo lo que ve el usuario final va en español: textos de UI, mensajes de error de las RPC y nombres de estado. Etiquetas de estado de cara al usuario exactamente como en RF-5 (Pendiente de envío, Enviado, Confirmado, Con problema, Rechazado). El esquema ya aplicado en Supabase (tablas, columnas, RPC como `enviar_pedido`) se mantiene en español y no se renombra; los valores internos de estado (`con_problema`, `sin_stock`, `precio_cambiado`) también.
- **Dinero en bolivianos con 2 decimales, nunca float**: `numeric(10,2)`/`numeric(12,2)` en BD, `BigDecimal` con `HALF_UP` en Android (guardado como `String` en Room), sumas en centavos enteros en la consola. Formato `Bs 16.50`.
- **Zona horaria `America/La_Paz`** (UTC-4 fijo) define los "pedidos del día".
- Clientes usan solo la **anon key**; la `service_role` solo en `backend/scripts/`.

## Arquitectura del backend

- Migraciones en `backend/migrations/` (`0001_schema` → `0002_rls` → `0003_rpc`), aplicadas **con el MCP de Supabase** (`apply_migration` para migraciones, `execute_sql` para `seed.sql`) usando el archivo del repo tal cual. **No hay Supabase CLI ni Docker**; no instalarlos. Un cambio de esquema = editar/agregar el archivo en el repo y aplicarlo vía MCP.
- **Nadie escribe directo en `pedidos`/`pedido_items`**: no hay políticas de insert/update/delete. Todo pasa por RPC `security definer`:
  - `enviar_pedido(p_id, p_cliente_id, p_items, p_creado_en)` (app): idempotente por el uuid generado en el teléfono; bloquea productos `FOR UPDATE` en orden de `id` (evita deadlocks y resuelve la carrera entre vendedores offline, RF-6); confirma y descuenta stock, o marca `con_problema` (`sin_stock` / `precio_cambiado` por ítem) sin tocar stock.
  - `resolver_pedido(p_id, p_accion, p_items_ajustados, p_nota)` (consola): solo supervisor, solo sobre `con_problema`; acciones `aprobar` / `ajustar` / `rechazar`.
  - Helper interno en el schema `private` (no expuesto a PostgREST).
- Convenciones de las funciones SQL: `security definer` con `set search_path = ''` (toda referencia a tablas va calificada: `public.pedidos`, `auth.uid()`); errores con `raise exception` en español y `errcode` `42501` (permiso) o `22023` (datos inválidos), que es el texto que ven app y consola; `enviar_pedido` serializa reintentos del mismo uuid con `pg_advisory_xact_lock`. Al agregar una función, repetir el `revoke execute ... from public, anon` + `grant ... to authenticated` del final de `0003_rpc.sql`.
- El supervisor edita productos con `UPDATE` directo; el grant de columna limita a `precio_bs` y `stock`.
- RLS se apoya en `es_supervisor()` y `es_vendedor_activo()` (leen `perfiles` por `auth.uid()` y exigen `activo = true`).
- Un trigger mantiene `actualizado_en` en `clientes`, `productos`, `pedidos`; la app sincroniza por delta sobre esa columna. `pedidos` está en la publicación `supabase_realtime` para la consola.
- Para probar RPC vía `execute_sql`, simular un usuario con `select set_config('request.jwt.claims', json_build_object('sub','<uuid>','role','authenticated')::text, true);` (ver requirements §8.4).
- Este `CLAUDE.md` sí se versiona (se quitó de `.gitignore` al cerrar la spec 02); `.claude/settings.local.json` no. El MCP de Supabase es el único activado en `.claude/settings.local.json`. El proyecto remoto ya tiene aplicadas las tres migraciones (verificable con `list_migrations`) y el seed completo (50 productos, 30 clientes, 11 perfiles/usuarios); no volver a aplicarlos salvo que cambien. Un cambio de esquema va en una migración nueva (`0004_...`), no editando las ya aplicadas.

## Comandos

Seed (Node 22, desde `backend/scripts/`, requiere `.env` copiado de `.env.example`):

```
npm install
node gen_seed.mjs        # CSV de backend/datos_semilla/ → backend/seed.sql (no editar seed.sql a mano)
node seed_usuarios.mjs   # crea/actualiza los 11 usuarios en Auth + perfiles (repetible)
```

Login de prueba: código → email (`V-01` → `v-01@distribuidora.test`, supervisor `S-01`), contraseña `SEED_PASSWORD` (por defecto `Distribuidora2026!`).

Consola: `cd consola-web && npm install && npm run dev` (necesita `.env.local` con `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`). `npm run build` corre `tsc -b` y `npm run lint` corre oxlint (`.oxlintrc.json`).

Android: Java no está en PATH; usar el JBR de Android Studio:

```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # o $env:JAVA_HOME en PowerShell
cd app-android && ./gradlew assembleDebug        # ./gradlew testDebugUnitTest para los tests de core/
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
```

Se prueba con dos AVD a la vez (A en `emulator-5554` con `V-01`, B en `emulator-5556` con `V-02`; la spec 02 se verificó con `Pixel_9a_V2` y `Pixel_9a`); compilar antes de levantar el segundo por RAM. Modo avión por AVD: `adb -s <serial> shell cmd connectivity airplane-mode enable|disable`. `adb -s <serial> emu avd name` dice qué AVD ocupa cada serial.

Tests automatizados: solo `app-android/app/src/test` (`MoneyTest`, `TimeTest`, `./gradlew testDebugUnitTest`) y oxlint en la consola. El resto es verificación manual según `docs/requirements.md` §6.4 y §8.7 y los criterios de aceptación de cada spec en `specs/`.

## Arquitectura de la app Android

- **Room es una copia; el servidor siempre gana.** `products`, `clients`, `orders`, `order_items`, `sync_meta` en `vendedor.db` (versión 1, migración destructiva). La bajada es por delta `actualizado_en >= lastSync` por tabla, con `lastSync` = mayor `actualizado_en` recibido (nunca el reloj del teléfono). Un pedido bajado reemplaza la cabecera local (incluso un `pendiente`) y sus ítems en una transacción (`OrderDao.replaceFromServer`).
- **Estado `pendiente` solo existe en el teléfono.** Un pedido nace `pendiente` con uuid generado en la app (clave de idempotencia de `enviar_pedido`), no descuenta stock local y pasa directo al estado que devuelve la RPC. Nunca se edita ni se borra desde la app.
- **Timestamps en UTC dentro de Room** (`Instant.toString()`, helpers en `core/Time.kt`); la conversión a `America/La_Paz` es solo al mostrar. Dinero: `BigDecimal` escala 2 `HALF_UP`, texto en Room y DTOs, `BigDecimalSerializer` emite literal numérico sin comillas hacia la RPC.
- **Sincronización** (`SyncRepository.syncAll()`, serializada con `Mutex`): perfil propio → subir pendientes → bajar catálogo → bajar pedidos propios. Se dispara al entrar al Home y al confirmar un pedido (`SyncScheduler.enqueueNow`, `OneTimeWorkRequest` único `sync-now` con `KEEP`), cada 15 min (`sync-periodic`) y con "Sincronizar ahora" (llamada directa con snackbar). `SyncWorker` espera `auth.awaitInitialization()`, no hace nada sin sesión y devuelve `retry()` solo ante error de red.
- **Errores** (`SyncError.from`): red/timeout → `Network` (nada cambia); HTTP 401/403 o perfil propio sin fila → `Auth` (RLS devuelve 0 filas para un vendedor inactivo; `forceLogout()` conserva los pedidos locales y Login muestra "Tu cuenta no está activa"); cualquier otro error de PostgREST → `Data(message)` (el pedido sigue `pendiente` con `attempts + 1` y `lastError` = texto de la RPC, y se continúa con el siguiente).
- **Salir** está bloqueado con pendientes ("Sincroniza antes de salir"); sin pendientes borra `orders`, `order_items` y el `sync_meta` de pedidos, limpia `SessionStore` y hace `signOut()`. Catálogo y clientes se conservan.
- **Navegación sin librería**: `AppRoot` elige Login/Home por `auth.sessionStatus` (`RefreshFailure` se queda donde está) y mantiene Login mientras `loginPhase != Idle` (chequeo de perfil + bajada inicial "Descargando catálogo…"). `HomeScreen` guarda la pestaña y `selectedOrderId`. Los ViewModels se crean con `viewModel { ... }` desde `Graph` y sobreviven a los cambios de pantalla (resetear estado al salir).
- **Toolchain**: el plugin clásico `org.jetbrains.kotlin.android` 2.2.x no es compatible con AGP 9.4 y KSP solo acepta el Kotlin integrado desde 2.3.1; no bajar esas versiones. `local.properties` con `sdk.dir` en barras `/`. `local.properties` no se commitea.
- **Pruebas por adb** (lo que funcionó): `adb exec-out screencap -p > archivo.png` para capturas (la primera tras arrancar puede salir negra; reintentar); `adb shell uiautomator dump //sdcard/ui.xml` + `exec-out cat` para leer textos de snackbars que ya desaparecieron (con `MSYS_NO_PATHCONV=1` en Git Bash); Room se vuelca con `adb exec-out run-as com.distribuidora.vendedor cat databases/vendedor.db` (y `-wal`) y se consulta con `platform-tools/sqlite3.exe`. No usar `keyevent 4` para cerrar el teclado en el formulario: sin foco de texto saca la app al launcher y el `input text` posterior cae en la barra de búsqueda; usar el botón "+" o tocar otro campo.
