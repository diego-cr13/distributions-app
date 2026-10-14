# Requerimientos del MVP – Distribuidora de abarrotes

## 1. Contexto del negocio
- **Empresa:** distribuidora andina de abarrotes.
- **Clientes:** tiendas de barrio.
- **Usuarios del sistema:**
  - **Vendedores de campo** (app Android): recorren mercados y zonas periurbanas.
  - **Supervisores** (consola web): trabajan desde la oficina, con conexión.
- **Entorno de campo:** zonas donde **muchas veces no hay conexión** a internet.

## 2. Problema actual (proceso en papel)
| Hoy | Consecuencia |
|-----|--------------|
| Los pedidos se anotan en papel | Se pierden pedidos |
| Se pasan a una planilla en la noche | Retraso; nadie ve el stock real durante el día |
| El vendedor no conoce el stock actualizado | Se venden productos que ya no hay |
| Los precios no se comparten en tiempo real | Los precios no coinciden |

**Objetivo del MVP:** reemplazar el papel con una primera versión del sistema (app Android + consola web + backend).

## 3. App móvil Android (vendedores)

### RF-1 Identificarse como vendedor
- El vendedor inicia sesión en su teléfono.
- **Método:** Supabase Auth con email + contraseña. El email se deriva del código del vendedor (`V-01` → `v-01@distribuidora.test`). Las cuentas las crea el script de seed con una contraseña inicial; no hay registro público.
- Al iniciar sesión, la app lee el rol en `perfiles` y rechaza a quien no sea `vendedor`.
- **Uso sin conexión:** la sesión queda guardada en el teléfono (supabase-kt) y sigue válida offline después del primer login. El token se renueva al volver la red; si la renovación falla (p. ej. cuenta desactivada), se pide login de nuevo.

### RF-2 Ver catálogo y clientes
- Catálogo: **producto, precio, stock**.
- Lista de **clientes** (tiendas).
- Se necesita copia local en el teléfono para consultarla sin red y actualizarla cuando haya conexión.

### RF-3 Crear un pedido
1. Elegir cliente.
2. Elegir productos y cantidades.
3. Ver el total.
4. Confirmar.

### RF-4 Funcionar sin conexión
- Se pueden crear pedidos sin red.
- Los pedidos pendientes se **envían solos** cuando vuelve la conexión (sincronización automática en segundo plano).
- No se debe perder ningún pedido (persistencia local) ni duplicarse al reintentar el envío.

### RF-5 Estado de cada pedido
| Estado | Significado |
|--------|-------------|
| Pendiente de envío | Guardado en el teléfono, todavía no llegó al servidor |
| Enviado | Llegó al servidor, falta validarlo |
| Confirmado | El servidor lo aceptó (hay stock y el precio es válido) |
| Con problema | El servidor lo observó (sin stock, precio cambiado, etc.); espera decisión del supervisor |
| Rechazado | El supervisor lo rechazó; no se entrega ni descuenta stock |

### RF-6 Varios vendedores a la vez
- Cada vendedor usa su propio teléfono.
- Todos venden **del mismo stock**.
- Riesgo: dos vendedores offline venden las mismas últimas unidades. El servidor resuelve el conflicto al recibir los pedidos y marca "Con problema" los que no se pueden cumplir.

## 4. Consola web (supervisores)

- **Usuarios:** supervisores de la distribuidora.
- **Entorno:** navegador de escritorio en la oficina, siempre con conexión. No requiere modo offline.

### RC-1 Identificarse como supervisor
- El supervisor inicia sesión en la consola web.
- El rol **supervisor** es distinto del rol **vendedor**: un vendedor no entra a la consola y un supervisor no crea pedidos desde la app.
- **Método:** el mismo de RF-1 (Supabase Auth, email + contraseña; `S-01` → `s-01@distribuidora.test`). La consola rechaza a quien no tenga rol `supervisor`.

### RC-2 Ver los pedidos que van llegando
- Lista de los pedidos del **día** (zona horaria `America/La_Paz`), que se actualiza en tiempo real (Supabase Realtime) a medida que los vendedores sincronizan.
- Cada pedido muestra: hora de llegada, vendedor, cliente, productos con cantidades, total y estado (Enviado / Confirmado / Con problema).
- Se puede filtrar y agrupar **por vendedor** y **por cliente**.
- **Total del día:** monto total y cantidad de pedidos, con desglose por vendedor.

### RC-3 Ver y cambiar stock y precios
- Catálogo completo: producto, precio, stock.
- El supervisor puede **editar el precio y el stock** de cada producto.
- El cambio se guarda en el backend (fuente de verdad) y llega a los teléfonos en la siguiente sincronización del catálogo.
- Los pedidos que lleguen después del cambio se validan contra el nuevo precio/stock.

### RC-4 Ver los pedidos con problema y decidir qué hacer
- Bandeja con los pedidos en estado **Con problema**, mostrando el motivo (sin stock, precio cambiado, etc.) y el detalle del pedido.
- El supervisor decide qué hacer con cada uno:

| Acción | Resultado |
|--------|-----------|
| Aprobar igual | Pasa a **Confirmado** con el precio que vio el vendedor; requiere stock suficiente y lo descuenta |
| Ajustar y aprobar | Modifica cantidades o precios, se revalida el stock, se descuenta y pasa a **Confirmado** |
| Rechazar | Pasa a **Rechazado** (sin tocar stock); el vendedor lo ve en la app |

- La decisión se refleja en la app del vendedor en la siguiente sincronización.

## 5. Backend (Supabase, fuente de verdad)
- El backend es la **fuente de verdad** del stock y los precios.
- Valida cada pedido al recibirlo (stock, precio vigente) y define su estado: Confirmado o Con problema.
- Los cambios hechos desde la consola web (precios, stock, decisiones sobre pedidos con problema) se aplican en el backend y llegan a las apps al sincronizar.

### 5.1 Plataforma
- **Supabase en la nube** (supabase.com): Postgres, Auth, Realtime y RPC vía PostgREST.
- App Android con `supabase-kt`; consola web con `supabase-js`.
- La app y la consola usan solo la **anon key**. La **service_role key** se usa únicamente en los scripts de seed y nunca se incluye en un cliente.
- Zona horaria del negocio: `America/La_Paz` (define qué son los "pedidos del día").

### 5.2 Moneda
- Todos los montos están en **bolivianos (Bs) con 2 decimales**.
- Base de datos: `numeric(10,2)` para precios y subtotales, `numeric(12,2)` para totales, con `CHECK (precio_bs > 0)`.
- Nunca se usa `float`/`double`. En Android se usa `BigDecimal`.
- Cálculo: subtotal = cantidad × precio, redondeado a 2 decimales (HALF_UP); total = suma de subtotales.
- Formato en pantalla: `Bs 16.50`.

### 5.3 Modelo de datos
| Tabla | Columnas principales | Restricciones / notas |
|-------|---------------------|-----------------------|
| `perfiles` | `id` (uuid → `auth.users`), `codigo`, `nombre`, `rol`, `activo` | `codigo` único; `rol` ∈ {`vendedor`, `supervisor`} |
| `clientes` | `id`, `codigo`, `nombre`, `zona`, `ciudad`, `actualizado_en` | `codigo` único |
| `productos` | `id`, `codigo`, `nombre`, `unidad`, `precio_bs numeric(10,2)`, `stock int`, `actualizado_en` | `codigo` único; `precio_bs > 0`; `stock >= 0` |
| `pedidos` | `id` (uuid **generado en el teléfono**), `vendedor_id`, `cliente_id`, `estado`, `motivo`, `total_bs numeric(12,2)`, `creado_en_dispositivo`, `recibido_en`, `resuelto_por`, `resuelto_en`, `nota_supervisor`, `actualizado_en` | `estado` ∈ {`enviado`, `confirmado`, `con_problema`, `rechazado`}; el `id` es la clave de idempotencia |
| `pedido_items` | `pedido_id`, `producto_id`, `cantidad`, `precio_visto_bs`, `precio_aplicado_bs`, `subtotal_bs` (todos `numeric(10,2)`), `problema` | `cantidad > 0`; `problema` ∈ {`sin_stock`, `precio_cambiado`, null} |

- Un trigger mantiene `actualizado_en` en cada cambio; se usa para la sincronización incremental.

### 5.4 Funciones (RPC)
Todas son `security definer` y corren en una sola transacción.

**`enviar_pedido(id, cliente_id, items jsonb, creado_en)`** (la llama la app)
1. Si el `id` ya existe, devuelve ese pedido tal cual: reintentar no lo duplica (RF-4).
2. Bloquea los productos del pedido con `SELECT … FOR UPDATE`, en orden de `id` para evitar deadlocks. Así un pedido concurrente siempre ve el stock ya descontado por el otro (RF-6).
3. Valida cada ítem: hay stock suficiente y `precio_visto_bs` = precio vigente.
4. Si todo es válido → `confirmado` y descuenta el stock.
5. Si algo falla → `con_problema`, con el motivo en cada ítem; **no** descuenta stock.
6. Devuelve el estado final y los motivos.

**`resolver_pedido(id, accion, items_ajustados jsonb, nota)`** (la llama la consola)
- Solo la puede ejecutar un supervisor y solo sobre pedidos `con_problema`.
- `aprobar`: usa el precio visto; exige stock suficiente y lo descuenta.
- `ajustar`: aplica las cantidades/precios nuevos, revalida el stock y lo descuenta.
- `rechazar`: pasa a `rechazado` sin tocar el stock.
- Registra `resuelto_por`, `resuelto_en` y `nota_supervisor`.

**Edición de precio/stock (RC-3):** `UPDATE` directo sobre `productos`, que RLS permite solo al supervisor.

### 5.5 Seguridad (RLS)
- Función auxiliar `es_supervisor()` que consulta `perfiles`.
- **Vendedor:**
  - Lee `productos` y `clientes`.
  - Lee solo sus propios `pedidos` y `pedido_items`.
  - No puede hacer INSERT/UPDATE directo: todo pasa por `enviar_pedido`.
- **Supervisor:**
  - Lee todas las tablas.
  - Actualiza `productos`.
  - Ejecuta `resolver_pedido`.
- Perfiles con `activo = false` no pasan las políticas.

### 5.6 Sincronización
- **Bajada (app):**
  - `productos` y `clientes` se bajan por delta (`actualizado_en > última_sync`).
  - También se bajan los pedidos propios del vendedor para ver cambios de estado y decisiones del supervisor.
- **Subida (app):**
  - Un WorkManager con restricción de red envía los pedidos pendientes llamando a `enviar_pedido`.
  - Reintenta con backoff y usa el mismo uuid en cada intento.
- **Consola:** Supabase Realtime sobre `pedidos` muestra los pedidos a medida que llegan.

### 5.7 Datos semilla
- **Origen:** `backend/datos_semilla/`.

| Archivo | Columnas | Contenido |
|---------|----------|-----------|
| `vendedores.csv` | `codigo,nombre,rol` | 10 vendedores (V-01..V-10) + 1 supervisor (S-01) |
| `clientes.csv` | `codigo,nombre,zona,ciudad` | 30 tiendas (La Paz, El Alto, Cochabamba, Santa Cruz) |
| `productos.csv` | `codigo,nombre,unidad,precio_bs,stock` | 50 productos; `precio_bs` en Bs con 2 decimales |

- **Esquema:** los SQL viven en el repo (`backend/migrations/`, `backend/seed.sql`) y se aplican con el **MCP de Supabase** (`apply_migration` para las migraciones, `execute_sql` para el seed), en este orden: `0001_schema.sql`, `0002_rls.sql`, `0003_rpc.sql`, `seed.sql`. El archivo del repo es la fuente: se aplica tal cual está escrito. **No se usa Supabase CLI ni Docker.**
- **Productos y clientes:** `backend/scripts/gen_seed.mjs` lee los CSV y genera `backend/seed.sql`. `precio_bs` se importa tal cual a `numeric(10,2)`.
- **Usuarios:** `backend/scripts/seed_usuarios.mjs` (Node + `@supabase/supabase-js` con la service_role) crea en Auth los 11 usuarios de `vendedores.csv`: email `codigo.toLowerCase() + '@distribuidora.test'`, contraseña inicial `Distribuidora2026!` (configurable con `SEED_PASSWORD`), `email_confirm: true`; luego hace `upsert` en `perfiles` por `codigo`. Busca cada usuario antes de crearlo.
- **Repetible:** el seed se puede correr varias veces sin duplicar (upsert por `codigo`).

## 6. Entorno de desarrollo

### 6.1 Herramientas en la laptop (verificado)
| Herramienta | Detalle |
|-------------|---------|
| Node | v22 (`node`, `npm` en PATH) |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`; platforms `android-35` y `android-37`; build-tools 36.0.0 y 37.0.0 |
| Java | Solo el JBR de Android Studio: `C:\Program Files\Android\Android Studio\jbr`. No está en PATH → definir `JAVA_HOME` al compilar con Gradle. |
| Supabase CLI | **No instalado y no se instala.** Todo se hace contra el proyecto en la nube. |
| Emuladores | AVD `Pixel_9_Pro_Vendedor-A` y `Pixel_9_Pro_Vendedor-B` (ya creados) |

### 6.2 Emuladores: los dos a la vez
- La app se prueba con **ambos AVD corriendo simultáneamente** en la laptop.
- Se conectan directo a la URL del proyecto, `https://<ref>.supabase.co`. Al ser nube, no hay que configurar `10.0.2.2` ni IPs locales.
- Instalación por serial (ver `adb devices`; normalmente `emulator-5554` y `emulator-5556`):
  ```
  adb -s emulator-5554 install -r app-android/app/build/outputs/apk/debug/app-debug.apk
  adb -s emulator-5556 install -r app-android/app/build/outputs/apk/debug/app-debug.apk
  ```
- `V-01` inicia sesión en Vendedor-A y `V-02` en Vendedor-B. La sesión y la base Room son por dispositivo; el único punto compartido es Supabase.
- Modo avión por AVD, de forma independiente: Extended Controls > Cellular/Wi-Fi, o
  ```
  adb -s emulator-5554 shell cmd connectivity airplane-mode enable
  adb -s emulator-5554 shell cmd connectivity airplane-mode disable
  ```
- RAM: compilar (`gradlew assembleDebug`) **antes** de levantar el segundo emulador.

### 6.3 Configuración (no se commitea; sí los `.example`)
| Dónde | Variables |
|-------|-----------|
| `app-android/local.properties` | `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` → expuestas vía `BuildConfig` |
| `consola-web/.env.local` | `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` |
| `backend/scripts/.env` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SEED_PASSWORD` |

### 6.4 Pruebas manuales
- **Prueba offline (RF-4):** modo avión en un AVD, crear pedidos, cerrar y abrir la app (siguen ahí y no pide login), reconectar.
- **Prueba de RF-6:**
  - Los dos AVD en modo avión, con vendedores distintos.
  - Vender offline las mismas últimas unidades de un producto (p. ej. `P-0004`, stock 12) en cada uno.
  - Reconectar ambos casi al mismo tiempo: uno queda Confirmado y el otro Con problema (`sin_stock`).
- **Prueba de precio cambiado:** con A en modo avión vendiendo `P-0001`, cambiar su precio desde la consola; al reconectar A, el pedido queda Con problema (`precio_cambiado`).

## 7. Decisiones del MVP (antes preguntas abiertas)

### App móvil
| Pregunta | Decisión |
|----------|----------|
| ¿Cómo se autentica el vendedor y quién crea las cuentas? | Supabase Auth email + contraseña. Las cuentas las crea el script de seed; no hay alta desde la UI. |
| ¿Qué precio vale: el visto offline o el vigente? | El vigente en el servidor. Si difiere → Con problema (`precio_cambiado`) y decide el supervisor. |
| ¿Se descuenta stock por pedido confirmado o se reserva? | Se descuenta al confirmar (automático o por el supervisor). No hay reservas. |
| ¿Un pedido con problema se edita y reenvía desde la app? | No. Lo resuelve el supervisor; el vendedor puede crear un pedido nuevo. |
| ¿Cada vendedor ve todos los clientes o solo su zona? | Todos los clientes (los datos semilla no asignan zona por vendedor). |
| ¿El vendedor puede crear clientes? | No en el MVP. |

### Consola web
| Pregunta | Decisión |
|----------|----------|
| ¿Solo supervisor o también admin? | Solo `supervisor` y `vendedor`. Las altas se hacen con el seed. |
| ¿Acciones sobre pedidos con problema? | Exactamente aprobar, ajustar y rechazar. |
| ¿Al aprobar un pedido observado por precio, qué precio vale? | El precio que vio el vendedor. Para usar el vigente, el supervisor usa "ajustar". |
| ¿"Rechazado" es estado propio? | Sí, agregado en RF-5. |
| ¿El supervisor gestiona clientes y vendedores? | No en el MVP. |
| ¿Tiempo real o refrescar? | Tiempo real con Supabase Realtime. |

### Plataforma y entorno
| Pregunta | Decisión |
|----------|----------|
| ¿Supabase local o en la nube? | **Nube** (proyecto ya creado en supabase.com). Sin CLI ni Docker: no hay instalación, el emulador conecta directo a la URL del proyecto y los dos AVD comparten el mismo backend, que es lo que exige RF-6. |
| ¿Idioma? | **Español** en la app y en la consola: textos de UI, mensajes de error y nombres de estado. |
| ¿Stack de la consola web? | Vite + React + TypeScript + `supabase-js`. Sin router ni backend propio. |
| ¿Stack de la app Android? | Kotlin, Jetpack Compose, Room, WorkManager, `supabase-kt` 3.x. Sin Hilt: inyección manual con un singleton en `Application`. |
| ¿Cómo se aplica el esquema? | Con el MCP de Supabase (`apply_migration`), en orden, a partir de los archivos del repo. |
| ¿Qué se recorta si falta tiempo? | Se construye en orden backend → consola → app. Lo que quede pendiente se documenta en `README.md`. |

## 8. Plan de implementación (tarea de un día)

### 8.1 Condiciones
- Es una **tarea de un solo día**.
- Supabase **en la nube**, proyecto ya creado (se tienen URL, anon key y service_role key).
- Consola web en **Vite + React + TypeScript**.
- **Idioma español** en toda la UI (app y consola).
- Pruebas en **dos emuladores a la vez**: `Pixel_9_Pro_Vendedor-A` y `Pixel_9_Pro_Vendedor-B`.

### 8.2 Orden de trabajo
1. **Backend**: migraciones, RLS, RPC, seed. Desbloquea todo lo demás.
2. **Consola web**: rápida de hacer y sirve para validar el backend antes de tocar Android.
3. **App Android**: la parte más larga (offline + sincronización).
4. **Pruebas RF-4 y RF-6** con los dos AVD.

Si falta tiempo, lo pendiente se anota en `README.md`.

### 8.3 Estructura del repo
```
backend/migrations/0001_schema.sql    tablas, checks, índices, trigger actualizado_en, publicación realtime
backend/migrations/0002_rls.sql       es_supervisor(), es_vendedor_activo(), políticas, grants
backend/migrations/0003_rpc.sql       enviar_pedido, resolver_pedido
backend/seed.sql                      upsert de productos y clientes (generado desde los CSV)
backend/datos_semilla/                vendedores.csv, clientes.csv, productos.csv
backend/scripts/package.json          @supabase/supabase-js
backend/scripts/gen_seed.mjs          CSV → backend/seed.sql
backend/scripts/seed_usuarios.mjs     usuarios en Auth + perfiles (service_role)
backend/scripts/.env.example
consola-web/                          Vite + React + TS + supabase-js
app-android/                          Kotlin + Compose + Room + WorkManager + supabase-kt
docs/requirements.md                  este documento
README.md
.gitignore                            local.properties, .env, .env.local, build/, node_modules/
```

### 8.4 Backend, detalle
**Esquema (`0001_schema.sql`)**
- Las tablas y checks de 5.3, más: `pedido_items.id bigserial pk`, `on delete cascade` de `pedido_items` → `pedidos`, `perfiles.id` → `auth.users on delete cascade`.
- Índices: `pedidos(vendedor_id, actualizado_en)`, `pedidos(recibido_en)`, `productos(actualizado_en)`, `clientes(actualizado_en)`, `pedido_items(pedido_id)`.
- Trigger `set_actualizado_en()` BEFORE UPDATE en `clientes`, `productos`, `pedidos`.
- `alter publication supabase_realtime add table pedidos;` para RC-2.

**RLS (`0002_rls.sql`)**
- `es_supervisor()` y `es_vendedor_activo()`: `security definer`, `stable`, consultan `perfiles` por `auth.uid()` y exigen `activo = true`.
- `perfiles`: select propio (`id = auth.uid()`) o supervisor.
- `productos`, `clientes`: select si vendedor activo o supervisor; update de `productos` solo supervisor.
- `pedidos`, `pedido_items`: select propio (`vendedor_id = auth.uid()`, items vía `exists` sobre `pedidos`) o supervisor. **Sin** insert/update/delete directos para nadie.
- `revoke execute` a `public`/`anon`; `grant execute` de las dos RPC solo a `authenticated`.

**`enviar_pedido(p_id uuid, p_cliente_id uuid, p_items jsonb, p_creado_en timestamptz) returns jsonb`**
- `p_items = [{producto_id, cantidad, precio_visto_bs}]`; `vendedor_id = auth.uid()`; exige `es_vendedor_activo()`.
- Si `p_id` ya existe → devuelve ese pedido sin tocar nada (idempotencia, RF-4).
- `select … from productos where id = any(ids) order by id for update` (RF-6).
- Por ítem: `sin_stock` si `stock < cantidad`; `precio_cambiado` si `precio_bs <> precio_visto_bs`; `subtotal = round(cantidad * precio_visto_bs, 2)`.
- Sin problemas → `confirmado`, `precio_aplicado_bs = precio_bs`, descuenta stock. Con problemas → `con_problema`, `motivo` resumen (`sin_stock: P-0004; precio_cambiado: P-0001`), stock intacto.
- Devuelve `{id, estado, motivo, total_bs, items: [{producto_id, problema, precio_vigente_bs}]}`.

**`resolver_pedido(p_id uuid, p_accion text, p_items_ajustados jsonb, p_nota text) returns jsonb`**
- Exige `es_supervisor()`; `for update` sobre el pedido; exige `estado = 'con_problema'`.
- `rechazar` → `rechazado`, sin tocar stock.
- `aprobar` → bloquea productos en orden, exige stock para todo, `precio_aplicado_bs = precio_visto_bs`, descuenta, `confirmado`. Si falta stock, `raise exception` con mensaje claro.
- `ajustar` → `p_items_ajustados = [{producto_id, cantidad, precio_bs}]` (cantidad 0 elimina el ítem); actualiza cantidad, `precio_aplicado_bs` y subtotal; recalcula `total_bs`; revalida stock; descuenta; `confirmado`.
- En los tres casos: `problema = null` en items, `resuelto_por = auth.uid()`, `resuelto_en = now()`, `nota_supervisor = p_nota`.

**Prueba con `execute_sql` (MCP)**: simular a `V-01` con `select set_config('request.jwt.claims', json_build_object('sub', '<uuid de V-01>', 'role', 'authenticated')::text, true);` y llamar `enviar_pedido`. Verificar: mismo uuid dos veces → una sola fila; cantidad > stock → `con_problema` y stock sin cambio.

### 8.5 Consola web, detalle (`consola-web/`)
- `npm create vite@latest consola-web -- --template react-ts`; dependencia `@supabase/supabase-js`. Sin router: estado `vista ∈ {pedidos, catalogo, problemas}` en `App.tsx`. CSS plano.
- `src/lib/supabase.ts`: cliente con las variables `VITE_*`.
- `src/lib/dinero.ts`: formato `Bs 16.50`; las sumas se hacen en **centavos enteros**, nunca con float.
- `src/lib/dia.ts`: inicio del día en `America/La_Paz` (UTC-4 fijo, sin horario de verano) como ISO para filtrar `recibido_en`.
- `components/Login.tsx` (RC-1): `signInWithPassword`; lee `perfiles` propio; si `rol !== 'supervisor'` → `signOut()` y mensaje en español.
- `components/PedidosDelDia.tsx` (RC-2): `select` con joins a `perfiles`, `clientes`, `pedido_items(productos)` y `recibido_en >= inicioDia`; canal Realtime `postgres_changes` sobre `pedidos` (insert/update) → refetch; filtros y agrupación por vendedor y por cliente; total del día y desglose por vendedor.
- `components/Catalogo.tsx` (RC-3): tabla con edición inline de precio y stock (`update productos`); valida `precio > 0`, `stock >= 0`, 2 decimales.
- `components/Problemas.tsx` (RC-4): lista `estado = 'con_problema'` con motivo por ítem; botones Aprobar / Ajustar (edita cantidades y precios) / Rechazar con nota; llama `rpc('resolver_pedido')` y muestra el error si falla.

### 8.6 App Android, detalle (`app-android/`)
- Package `com.distribuidora.vendedor`; minSdk 26, targetSdk 35; Kotlin 2.0.x, AGP 8.7.x, Gradle 8.9 (wrapper descargado del repo oficial de Gradle); KSP; Room 2.6.x; WorkManager 2.9.x; `supabase-kt` 3.x (`postgrest-kt`, `auth-kt`) + `ktor-client-okhttp`; `kotlinx-serialization`.
- Sin Hilt: un objeto `Grafo` en `App.kt` con Room, `SupabaseClient` y repositorios.
- **Room**: `ProductoEntity`, `ClienteEntity`, `PedidoEntity` (`id` uuid string, `clienteId`, `estado ∈ {pendiente, enviado, confirmado, con_problema, rechazado}`, `motivo`, `totalBs: String`, `creadoEnDispositivo`, `intentos`), `PedidoItemEntity` (`pedidoId`, `productoId`, `cantidad`, `precioVistoBs`, `subtotalBs`, `problema`), `SyncMetaEntity` (`tabla`, `ultimaSync`). Montos como `String` ↔ `BigDecimal` con `setScale(2, HALF_UP)`.
- **Auth** (RF-1): `AuthRepository.login(codigo, password)` → email derivado → `signInWith(Email)`; lee `perfiles`; si `rol != "vendedor"` → `signOut` + error. `supabase-kt` guarda la sesión en SharedPreferences, así que tras el primer login la app abre directo al Home aunque no haya red. Si el refresh falla por error de auth (no de red) → Login.
- **Sync** (`SyncRepository`): `bajarCatalogo()` (productos y clientes con `actualizado_en > ultimaSync`, upsert en Room), `bajarPedidosPropios()` (actualiza estado/motivo de pedidos ya enviados), `subirPendientes()` (por cada pedido `pendiente` → `rpc("enviar_pedido")` con el mismo uuid → guarda estado y motivo).
- **`SyncWorker`** (CoroutineWorker): subir → bajar. `OneTimeWorkRequest` único (`ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`, backoff exponencial 30 s) encolado al confirmar un pedido y al abrir la app; más `PeriodicWorkRequest` cada 15 min.
- **UI (Compose)**: `LoginScreen`; `HomeScreen` con tabs Pedidos / Nuevo / Catálogo / Clientes, contador de pendientes y botón "Sincronizar ahora"; `NuevoPedidoScreen` (RF-3: cliente con buscador, productos con cantidad, total en vivo, Confirmar → Room `pendiente` + encola worker); `PedidosScreen` (RF-5: lista con estado y color, detalle con motivo por ítem y nota del supervisor).
- **Idioma**: `res/values/strings.xml` en español como recurso por defecto, sin traducciones. Etiquetas de estado exactamente como en RF-5.

### 8.7 Verificación
1. **Backend**: en el dashboard hay 50 productos, 30 clientes, 11 usuarios en Auth y 11 perfiles. `enviar_pedido` dos veces con el mismo uuid → una fila. Cantidad > stock → `con_problema` y stock sin cambio.
2. **Consola**: `npm run dev`; `S-01` entra, `V-01` es rechazado. Editar precio de `P-0001` y verlo en el dashboard. Un pedido nuevo aparece sin refrescar.
3. **Android, ambos AVD**: `gradlew assembleDebug`; instalar en A y B; toda la UI en español. `V-01` en A, `V-02` en B. A en modo avión crea 2 pedidos; cerrar/abrir → siguen Pendiente de envío y no pide login; reconectar → Confirmado/Con problema y visibles en la consola.
4. **RF-6 en paralelo**: A y B en modo avión piden 12 unidades de `P-0004`; reconectar ambos → uno Confirmado, otro Con problema (`sin_stock`). Rechazar el segundo en la consola → ese AVD lo ve Rechazado al sincronizar. Caso precio: cambiar `P-0001` en la consola con A offline → `precio_cambiado`.
