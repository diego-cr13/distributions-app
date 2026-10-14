# SPEC 02 — App Android de vendedores

> **Estado:** Aprobado
> **Depende de:** SPEC 01 (solo para verificar: la consola muestra los pedidos que llegan, cambia precios y resuelve los pedidos con problema). El backend de `backend/migrations/` y el seed ya están aplicados en el proyecto Supabase.
> **Fecha:** 2026-10-14
> **Objetivo:** Construir en `app-android/` la app Kotlin + Compose con la que un vendedor entra con su cuenta, consulta catálogo y clientes sin conexión, crea pedidos que se guardan en el teléfono y se envían solos al volver la red, y ve el estado de cada pedido tal como lo decide el servidor.

## Por qué existe esta spec

`docs/requirements.md` §8.6 ya fija stack, entidades Room, repositorios, worker y pantallas de la app, pero deja abiertas decisiones de comportamiento: qué se baja del servidor, qué pasa con un pedido que el servidor rechaza por datos, cómo se trata el stock local al vender, cuándo se sincroniza, qué lista la pestaña Pedidos y cómo se arma el proyecto Gradle sin `gradle` en el PATH. Esta spec cierra esas decisiones y define cómo verificar la app con los dos AVD y la consola de la spec 01.

## Alcance

**Dentro:**

- Proyecto Gradle en `app-android/` con un módulo `app`, package `com.distribuidora.vendedor`, minSdk 26, compileSdk y targetSdk 35, versiones pineadas en `gradle/libs.versions.toml` (toolchain Gradle 9.6 + AGP 9.4 + Kotlin 2.2, ver "Decisiones"; librerías según §8.6), wrapper de Gradle 9.6.0 commiteado.
- `SUPABASE_URL` y `SUPABASE_ANON_KEY` leídos de `local.properties` y expuestos por `BuildConfig`.
- RF-1: login con código de vendedor y contraseña; rechazo de quien no tenga rol `vendedor` o esté inactivo; sesión persistida por `supabase-kt`; la app abre directo al Home sin red tras el primer login; botón Salir.
- RF-2: copia local en Room de `productos` y `clientes` con bajada por delta sobre `actualizado_en`; pestañas Catálogo y Clientes con buscador, usables sin red.
- RF-3: pantalla Nuevo pedido: elegir cliente, elegir productos y cantidades, total en vivo, Confirmar. El pedido se guarda en Room como `pendiente` con un uuid generado en el teléfono.
- RF-4: `SyncWorker` (WorkManager, restricción de red, backoff) que sube los pedidos pendientes con `rpc("enviar_pedido")` y luego baja catálogo, clientes y pedidos propios; sincronización al abrir, al confirmar un pedido, cada 15 minutos y con el botón "Sincronizar ahora".
- RF-5: pestaña Pedidos con todos los pedidos locales, etiqueta y color por estado, contador de pendientes, y detalle con ítems, problema por ítem, motivo y nota del supervisor.
- Bajada de los pedidos propios **completos** (cabecera e ítems) para recuperar historial y ver los ajustes del supervisor.
- Toda la UI y los mensajes en español en `res/values/strings.xml`; etiquetas de estado exactamente como en RF-5.
- Un test unitario local para los helpers de dinero (`app/src/test/`), el único test automatizado de la app.
- Actualización de `README.md` (puesta en marcha de la app y "Pendientes") y de `app-android/local.properties.example` si cambia.

**Fuera de alcance (para otras specs):**

- Cualquier cambio en el backend (migraciones, RLS, RPC). Lo que la app necesita ya existe: RLS de vendedor sobre `productos`, `clientes`, `pedidos` y `pedido_items`, `enviar_pedido` y `actualizado_en` en las tres tablas sincronizadas.
- Editar o borrar un pedido desde la app, en cualquier estado (§7: el vendedor crea otro pedido).
- Crear clientes, cambiar contraseña, recuperar contraseña, registro.
- Realtime en la app (la app sincroniza por worker y botón).
- Notificaciones push o locales cuando el supervisor decide.
- Filtro por fecha o reportes en la pestaña Pedidos.
- Build de release, firma, ProGuard, Play Store. Solo `assembleDebug`.
- Migraciones de Room entre versiones del esquema local (destructiva en el MVP).
- Tests instrumentados o de UI.

## Modelo de datos

La app no crea tablas ni columnas en Supabase. Lee `perfiles` (el propio), `productos`, `clientes`, `pedidos` y `pedido_items`, y llama a `enviar_pedido`. Los nombres de columnas y los valores internos de estado quedan en español porque son el esquema ya aplicado. Todo el código nuevo (archivos, clases, propiedades, comentarios) va en inglés; los nombres de §8.6 (`Grafo`, `ProductoEntity`, `NuevoPedidoScreen`) se traducen como hizo la spec 01.

### Room (`data/local/`), base `vendedor.db`, versión 1

```kotlin
@Entity(tableName = "products")
data class ProductEntity(
  @PrimaryKey val id: String,          // uuid
  val code: String,                    // productos.codigo, ej. "P-0001"
  val name: String,
  val unit: String,
  val priceBs: String,                 // numeric(10,2) como texto "16.50"
  val stock: Int,
  val updatedAt: String,               // actualizado_en ISO-8601
)

@Entity(tableName = "clients")
data class ClientEntity(
  @PrimaryKey val id: String,
  val code: String,                    // "C-0001"
  val name: String,
  val zone: String,
  val city: String,
  val updatedAt: String,
)

@Entity(tableName = "orders")
data class OrderEntity(
  @PrimaryKey val id: String,          // uuid generado en el teléfono; clave de idempotencia
  val clientId: String,
  val status: String,                  // pendiente | enviado | confirmado | con_problema | rechazado
  val reason: String?,                 // pedidos.motivo
  val totalBs: String,                 // "123.50"
  val createdOnDevice: String,         // creado_en_dispositivo, normalizado a UTC (Instant.toString())
  val receivedAt: String?,             // recibido_en en UTC (solo si vino del servidor)
  val supervisorNote: String?,         // nota_supervisor
  val attempts: Int,                   // intentos de envío
  val lastError: String?,              // mensaje del último envío fallido (solo mientras pendiente)
  val updatedAt: String?,              // actualizado_en del servidor (null mientras pendiente)
)

@Entity(tableName = "order_items", primaryKeys = ["orderId", "productId"])
data class OrderItemEntity(
  val orderId: String,
  val productId: String,
  val quantity: Int,                   // entera, igual que productos.stock y pedido_items.cantidad
  val priceSeenBs: String,             // precio_visto_bs
  val priceAppliedBs: String?,         // precio_aplicado_bs (lo pone el servidor)
  val subtotalBs: String,              // quantity × priceSeenBs, HALF_UP a 2 decimales
  val problem: String?,                // sin_stock | precio_cambiado | null
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
  @PrimaryKey val table: String,       // "products" | "clients" | "orders"
  val lastSync: String,                // mayor actualizado_en recibido para esa tabla
)
```

El estado local `pendiente` solo existe en la app. Los otros cuatro valores son los del esquema. Un pedido creado en el teléfono nace `pendiente` y pasa directamente al estado que devuelve `enviar_pedido` (`confirmado` o `con_problema`); `enviado` solo se vería si un pedido bajado del servidor lo tuviera.

Reglas de la copia local de pedidos:

- **El servidor siempre gana.** Si un pedido llega en la bajada, el servidor ya lo recibió (aunque la respuesta del envío se haya perdido). El upsert reemplaza la cabecera local, incluido un `pendiente`, con los datos del servidor.
- **Los ítems se reemplazan, no se mezclan.** Al bajar un pedido se borran sus `order_items` locales y se insertan los del servidor, con `subtotal_bs` y `precio_aplicado_bs` del servidor. Así un ítem eliminado por "Ajustar" (cantidad 0) desaparece también en la app.
- **El stock local no se descuenta** al crear un pedido `pendiente`. El catálogo local solo cambia al sincronizar; el servidor es quien descuenta.
- **Todos los timestamps se guardan en UTC** (`Instant.toString()`, por ejemplo `2026-10-14T13:15:00Z`), vengan del teléfono o de PostgREST (que devuelve `+00:00`). Así el orden por `createdOnDevice` como texto es correcto. La conversión a `America/La_Paz` se hace solo al mostrar.

### DTOs de PostgREST (`data/remote/Dtos.kt`)

`ProductDto`, `ClientDto` con los nombres de columna en español (`@SerialName("precio_bs")`, etc.). `OrderDto` con embed `pedido_items(*)` → `OrderItemDto`. `SendOrderResponse` con la forma exacta que devuelve la RPC:

```json
{ "id": "…", "estado": "confirmado", "motivo": null, "total_bs": 123.50,
  "items": [ { "producto_id": "…", "problema": null, "precio_vigente_bs": 16.50 } ] }
```

Payload de `rpc("enviar_pedido")`:

```json
{ "p_id": "<uuid>", "p_cliente_id": "<uuid>", "p_creado_en": "2026-10-14T09:15:00-04:00",
  "p_items": [ { "producto_id": "<uuid>", "cantidad": 3, "precio_visto_bs": 16.50 } ] }
```

### Dinero (`core/Money.kt`)

- Todo monto en memoria es `BigDecimal` con escala 2 y `RoundingMode.HALF_UP`; en Room y en los DTOs se guarda como texto. Nunca `Double`.
- `BigDecimalSerializer` (kotlinx-serialization): al leer acepta el número JSON tal cual (`JsonPrimitive.content` → `BigDecimal`); al escribir emite un literal numérico sin comillas (`JsonUnquotedLiteral(value.toPlainString())`), así `precio_visto_bs` llega a la RPC como número con 2 decimales exactos.
- `formatBs(value: BigDecimal): String` → `Bs 16.50` (`setScale(2, HALF_UP).toPlainString()`).
- `subtotal(quantity: Int, price: BigDecimal)` y `total(items)` suman `BigDecimal`.

### Tiempo (`core/Time.kt`)

- `nowUtc()`: `Instant.now().toString()`; se usa para `creado_en_dispositivo` (la RPC recibe `timestamptz`, el offset `Z` es válido).
- `toUtc(iso: String)`: normaliza cualquier ISO-8601 con offset (lo que devuelve PostgREST) a `Instant.toString()` antes de guardar en Room.
- `formatLaPaz(iso: String)`: `dd/MM HH:mm` en `America/La_Paz` para listas y detalle.
- Los `lastSync` de `sync_meta` se toman del mayor `actualizado_en` **recibido**, nunca del reloj del teléfono.

### Etiquetas (`core/Labels.kt`)

Mapa de estado → etiqueta RF-5: `pendiente` → "Pendiente de envío", `enviado` → "Enviado", `confirmado` → "Confirmado", `con_problema` → "Con problema", `rechazado` → "Rechazado". Problema → texto: `sin_stock` → "Sin stock", `precio_cambiado` → "Precio cambiado". Colores: Pendiente gris, Enviado azul, Confirmado verde, Con problema ámbar, Rechazado rojo.

### Sesión (`core/SessionStore.kt`)

`SharedPreferences` con `vendor_code` y `vendor_name` del perfil, para mostrar la cabecera sin red. Se borra al Salir y cuando la sesión deja de ser válida.

### Salir y datos locales

Salir está bloqueado mientras haya pedidos `pendiente` ("Sincroniza antes de salir"). Sin pendientes, `AuthRepository.logout()` borra `orders`, `order_items` y la fila `orders` de `sync_meta`, limpia `SessionStore` y hace `signOut()`. Catálogo y clientes se conservan. Así otro vendedor que entre en el mismo teléfono nunca ve ni envía pedidos ajenos.

### Errores de sincronización (`data/repository/SyncError.kt`)

PostgREST mapea los `errcode` de las RPC a HTTP: `42501` → 403, `22023` y `P0001` → 400. La clasificación es por tipo de excepción y HTTP:

- `Network`: excepción de red de Ktor (`IOException`, timeout). Nada cambia; el worker reintenta.
- `Auth`: HTTP 401 o 403, o el perfil propio no existe (RLS devuelve 0 filas cuando el vendedor está inactivo). Se hace `logout` forzado (sin borrar pedidos) y Login con "Tu cuenta no está activa".
- `Data(message)`: cualquier otro error de PostgREST (HTTP 400 y similares). El mensaje es el que devuelve la RPC, tal cual.

RLS no lanza error para un vendedor inactivo: `productos`, `clientes`, `pedidos` y `perfiles` simplemente devuelven 0 filas. Por eso `syncAll()` empieza leyendo el perfil propio y trata "sin fila" como `Auth`.

### Navegación

Sin librería de navegación. `ui/AppRoot.kt` mantiene `screen: Screen` (`Login`, `Home`) según `auth.sessionStatus`, y `HomeScreen` mantiene la pestaña activa (`Orders`, `NewOrder`, `Catalog`, `Clients`) y `selectedOrderId: String?` para el detalle.

## Archivos

```
app-android/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  gradle/libs.versions.toml                       versiones pineadas (§8.6 + toolchain de "Decisiones")
  gradle/wrapper/gradle-wrapper.jar, gradle-wrapper.properties (9.6.0)
  gradlew, gradlew.bat
  local.properties.example                        ya existe
  app/build.gradle.kts                            plugins android (Kotlin integrado), compose, ksp, serialization; BuildConfig
  app/src/main/AndroidManifest.xml                INTERNET, android:name=".App"
  app/src/main/res/values/strings.xml             todos los textos, en español
  app/src/main/res/values/themes.xml
  app/src/main/java/com/distribuidora/vendedor/
    App.kt                                        Application; object Graph (db, supabase, repos, scheduler)
    MainActivity.kt                               setContent { AppRoot() }
    core/Money.kt, Time.kt, Labels.kt, SessionStore.kt
    data/local/AppDatabase.kt                     Room, versión 1, fallbackToDestructiveMigration
    data/local/Entities.kt                        las cinco entidades
    data/local/Daos.kt                            ProductDao, ClientDao, OrderDao, SyncMetaDao
    data/remote/SupabaseClientFactory.kt          createSupabaseClient { install(Auth); install(Postgrest) }
    data/remote/Dtos.kt                           ProductDto, ClientDto, OrderDto, OrderItemDto, SendOrderResponse
    data/repository/AuthRepository.kt             login, logout (borra pedidos locales), fetchOwnProfile, session flow
    data/repository/OrderRepository.kt            createPendingOrder, flows de lista/detalle, contador, clearLocalOrders
    data/repository/SyncRepository.kt             checkProfile, uploadPending, downloadCatalog, downloadOwnOrders, syncAll
    data/repository/SyncError.kt                  clasificación: Network, Auth, Data(message)
    sync/SyncWorker.kt                            CoroutineWorker: syncAll; retry solo en Network
    sync/SyncScheduler.kt                         enqueueNow (KEEP), ensurePeriodic (15 min)
    ui/AppRoot.kt                                 Login / Home según sesión
    ui/theme/Theme.kt
    ui/login/LoginScreen.kt, LoginViewModel.kt
    ui/home/HomeScreen.kt, HomeViewModel.kt       tabs, contador de pendientes, Sincronizar ahora, Salir
    ui/orders/OrdersScreen.kt, OrderDetailScreen.kt, OrdersViewModel.kt
    ui/neworder/NewOrderScreen.kt, NewOrderViewModel.kt
    ui/catalog/CatalogScreen.kt
    ui/clients/ClientsScreen.kt
  app/src/test/java/com/distribuidora/vendedor/core/MoneyTest.kt
```

## Plan de implementación

Cada paso deja la app compilando con `./gradlew assembleDebug` (con `JAVA_HOME` apuntando al JBR de Android Studio) e instalable en el AVD A.

1. **Scaffold.** No hay `gradle` en el PATH; el wrapper se genera con la distribución cacheada en `~/.gradle/wrapper/dists/gradle-9.6.0-bin/*/gradle-9.6.0/bin/gradle wrapper --gradle-version 9.6.0` y se commitean `gradlew`, `gradlew.bat` y `gradle/wrapper/`. `settings.gradle.kts`, `build.gradle.kts`, `libs.versions.toml` (Gradle 9.6.0, AGP 9.4.1 con Kotlin integrado, Kotlin 2.2.10 en el classpath para los plugins de Compose y serialization, KSP 2.3.6, Compose BOM 2025.05.00, Material 3, activity-compose, lifecycle-viewmodel-compose, Room 2.7.x, WorkManager 2.9.x, supabase-kt 3.x `postgrest-kt` + `auth-kt`, `ktor-client-okhttp` de la línea que exige supabase-kt, kotlinx-serialization, JUnit 4). `gradle.properties` con `android.useAndroidX=true` y memoria para el daemon. `app/build.gradle.kts` activa `buildFeatures { buildConfig = true }`, lee `SUPABASE_URL` y `SUPABASE_ANON_KEY` de `local.properties` y los expone en `BuildConfig`; si faltan, la build falla con un mensaje claro. `MainActivity` muestra solo "Vendedores". Prueba: `assembleDebug` genera el APK y al instalarlo en A se ve el título.
2. **Helpers.** `Money.kt`, `Time.kt`, `Labels.kt` y `MoneyTest.kt`. Prueba: `./gradlew testDebugUnitTest` verde con `formatBs(BigDecimal("16.5"))` → `Bs 16.50`, `subtotal(3, 5.555)` → `16.67`, el serializer emite `16.50` sin comillas y `toUtc("2026-10-14T09:15:00-04:00")` → `2026-10-14T13:15:00Z`.
3. **Room y Graph.** Entidades, DAOs (upserts, `Flow` de productos y clientes con filtro de texto, `Flow` de pedidos ordenados por `createdOnDevice` desc con los `pendiente` primero, `Flow` del contador de pendientes, detalle con ítems), `AppDatabase`, `Graph` en `App.kt`. Prueba: compila e instala; la app sigue mostrando el título.
4. **Login y sesión (RF-1).** `SupabaseClientFactory`, `AuthRepository.login(code, password)`: email `code.lowercase() + "@distribuidora.test"`, `signInWith(Email)`, lee `perfiles` propio; si no hay fila (RLS oculta a los inactivos) o `rol != "vendedor"` → `signOut()` y "Solo un vendedor activo puede entrar a la app"; guarda código y nombre en `SessionStore`. Mensajes de login: sin red → "No hay conexión", credenciales inválidas → "Código o contraseña incorrectos". `AppRoot` observa `sessionStatus`: `Authenticated` → Home, `NotAuthenticated` → Login, `RefreshFailure` por red → se queda donde está, `Initializing` → pantalla en blanco. Home provisional con nombre del vendedor y Salir; Salir borra los pedidos locales (ver "Salir y datos locales") y en este paso, sin pedidos todavía, solo cierra sesión. Prueba: `V-01` entra, `S-01` ve el mensaje, contraseña mala muestra "Código o contraseña incorrectos", cerrar la app en modo avión y abrirla vuelve al Home sin pedir login, Salir vuelve al Login.
5. **Bajada y pestañas de consulta (RF-2).** `SyncRepository.downloadCatalog()` (productos y clientes con `actualizado_en gte lastSync`, orden `actualizado_en`, upsert, actualiza `sync_meta`) y `downloadOwnOrders()` (pedidos con embed `pedido_items(*)`, mismo delta; RLS limita al vendedor; el servidor siempre gana: upsert de la cabecera aunque el pedido local esté `pendiente`, y borrado e inserción de los ítems en una transacción). Los timestamps se normalizan con `toUtc()` antes de guardar. Si el cliente de un pedido bajado no existe en Room, la lista muestra el `clientId` como respaldo. Sincronización inicial tras el login con pantalla "Descargando catálogo…"; si falla, entra al Home con el aviso "No se pudo sincronizar". `CatalogScreen` (código, nombre, unidad, precio, stock, buscador) y `ClientsScreen` (código, nombre, zona, ciudad, buscador) leen Room. Prueba: tras el login en A hay 50 productos, 30 clientes y los pedidos de prueba de `V-01` en Room; en modo avión las dos pestañas siguen funcionando.
6. **Pedidos y detalle (RF-5).** `HomeScreen` con tabs Pedidos / Nuevo / Catálogo / Clientes. `OrdersScreen`: fecha y hora La Paz, cliente, cantidad de ítems, total `Bs 0.00`, badge de estado, contador en la pestaña, `lastError` en rojo si existe. `OrderDetailScreen`: ítems con "3 × Arroz Grano de Oro 1 kg", precio visto, precio aplicado si lo hay, problema por ítem, motivo y nota del supervisor. Prueba: los pedidos de `V-01` bajados en el paso 5 muestran etiquetas RF-5 exactas y, en los resueltos, la nota del supervisor.
7. **Nuevo pedido (RF-3).** `NewOrderScreen`: selector de cliente con buscador, lista de productos con buscador y stepper de cantidad (entera ≥ 0), aviso ámbar "Stock local: N" cuando la cantidad supera el stock local sin bloquear, barra inferior con total en vivo y Confirmar (deshabilitado sin cliente o sin ítems). `OrderRepository.createPendingOrder()` genera `UUID.randomUUID()`, calcula subtotales y total con `BigDecimal`, guarda cabecera e ítems en una transacción con `status = "pendiente"`, `attempts = 0`, `createdOnDevice = nowUtc()`, limpia el formulario y vuelve a Pedidos. El stock local no se toca. Precondiciones que exige `enviar_pedido` y la app garantiza antes de guardar: cliente elegido de la copia local, al menos un ítem, cantidad entera > 0, un producto una sola vez (PK `orderId, productId`), `precio_visto_bs` con exactamente 2 decimales (serializer). Prueba: en A con modo avión crear dos pedidos, cerrar y abrir la app → siguen "Pendiente de envío" con su total.
8. **Subida y worker (RF-4).** `SyncRepository.uploadPending()`: por cada `pendiente` en orden de `createdOnDevice`, `rpc("enviar_pedido")` con el mismo uuid; con respuesta, guarda `status`, `reason`, `totalBs`, `problem` por ítem y borra `lastError`. `syncAll()` = verificar perfil propio (`checkProfile()`) → subir → bajar catálogo → bajar pedidos. `SyncWorker` espera `auth.awaitInitialization()`; sin sesión devuelve `Result.success()` sin hacer nada; con sesión llama `syncAll()` y devuelve `Result.retry()` solo ante error de red. `SyncScheduler.enqueueNow()`: `OneTimeWorkRequest` único `sync-now`, `ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`, backoff exponencial 30 s; `ensurePeriodic()`: `PeriodicWorkRequest` único `sync-periodic` cada 15 min, `KEEP`. Se encola al entrar al Home y al confirmar un pedido. "Sincronizar ahora" en la cabecera llama `syncAll()` directo con indicador de progreso y resultado ("Sincronizado" o el mensaje de error). Prueba: quitar el modo avión en A → los dos pedidos pasan a Confirmado / Con problema solos y aparecen en la consola; enviar dos veces el mismo pedido (sync manual durante el worker) no lo duplica.
9. **Errores, expulsión y Salir.** `SyncError` clasifica como define "Errores de sincronización": red → `Network`, pedido sin cambios; HTTP 401/403 o perfil propio sin fila → `Auth`, logout forzado sin borrar pedidos y Login con "Tu cuenta no está activa"; otro error de PostgREST → `Data(message)`, el pedido sigue `pendiente`, `attempts + 1`, `lastError = message` tal cual lo devuelve la RPC, y el worker continúa con el siguiente pedido. Salir: bloqueado con "Sincroniza antes de salir" si hay pendientes; sin pendientes borra pedidos locales y `sync_meta` de pedidos y cierra sesión. Prueba: desactivar `V-01` por SQL (`update perfiles set activo = false`) y tocar "Sincronizar ahora" sin pedidos pendientes → vuelve al Login con el mensaje; reactivar y entrar de nuevo → los pedidos locales siguen. Con un pedido pendiente, Salir muestra el bloqueo; tras sincronizar, Salir cierra sesión y al entrar `V-02` la pestaña Pedidos empieza vacía hasta sincronizar.
10. **Acabado.** Colores por estado, estados de carga y vacío ("Sin pedidos todavía", "Sin resultados"), todos los textos en `strings.xml`, `README.md` con la puesta en marcha real (`JAVA_HOME`, `gradlew assembleDebug`, instalación en los dos seriales) y la sección "Pendientes" de la app. `CLAUDE.md` local: `Grafo` → `Graph`. Prueba: `assembleDebug` y `testDebugUnitTest` sin errores; recorrido de §8.7 pasos 3 y 4 con los dos AVD.

## Criterios de aceptación

- [ ] `./gradlew assembleDebug` y `./gradlew testDebugUnitTest` terminan sin errores con `JAVA_HOME` en el JBR de Android Studio.
- [ ] El APK se instala en `emulator-5554` y `emulator-5556` y toda la UI está en español.
- [ ] `V-01` con la contraseña del seed entra y ve su nombre en la cabecera; `S-01` ve "Solo un vendedor activo puede entrar a la app" y no queda con sesión.
- [ ] Con la app cerrada y el AVD en modo avión, abrirla muestra el Home sin pedir login; Salir vuelve al Login.
- [ ] Tras el primer login, Catálogo muestra 50 productos y Clientes 30 tiendas, con precio `Bs 0.00` y stock, y ambos se consultan en modo avión.
- [ ] Cambiar el precio de un producto desde la consola y tocar "Sincronizar ahora" actualiza ese precio en Catálogo sin bajar las otras filas (delta por `actualizado_en`).
- [ ] Los pedidos de `V-01` creados por SQL en la spec 01 aparecen en Pedidos con sus ítems, estado y nota del supervisor.
- [ ] En modo avión se crean dos pedidos; cerrar y abrir la app los muestra "Pendiente de envío" y el contador de la pestaña marca 2.
- [ ] Un ítem con cantidad mayor al stock local muestra el aviso ámbar y aun así se puede confirmar.
- [ ] Confirmar sin cliente o sin ítems está deshabilitado; la cantidad solo acepta enteros ≥ 1.
- [ ] Al quitar el modo avión, sin tocar nada, los pedidos pendientes pasan a Confirmado o Con problema y aparecen en la consola con el mismo uuid.
- [ ] Repetir el envío del mismo pedido (worker y botón a la vez, o reinstalar sin perder Room) no crea una segunda fila en `pedidos`.
- [ ] Un pedido Con problema muestra en el detalle "Sin stock" o "Precio cambiado" por ítem y el motivo del servidor.
- [ ] Rechazar ese pedido en la consola con nota y sincronizar en el AVD lo muestra Rechazado con la nota del supervisor.
- [ ] Ajustar un pedido en la consola y sincronizar muestra en el detalle las cantidades y precios aplicados nuevos.
- [ ] RF-6: A y B en modo avión piden 12 unidades de `P-0004`; al reconectar ambos, uno queda Confirmado y el otro Con problema (`sin_stock`), y el stock en la consola bajó una sola vez.
- [ ] Precio cambiado: A en modo avión vende `P-0001`, se cambia su precio en la consola, A reconecta → el pedido queda Con problema (`precio_cambiado`).
- [ ] Un pedido que la RPC rechaza por datos sigue "Pendiente de envío" con el mensaje de la RPC visible en la lista, y los demás pendientes se envían igual.
- [ ] Con `V-01` desactivado por SQL y sin pedidos pendientes, "Sincronizar ahora" manda al Login con "Tu cuenta no está activa"; los pedidos locales siguen en Room al volver a entrar.
- [ ] Salir con pedidos pendientes muestra "Sincroniza antes de salir" y no cierra sesión; sin pendientes cierra sesión, y al entrar `V-02` en ese teléfono la pestaña Pedidos empieza vacía hasta sincronizar, mientras Catálogo y Clientes siguen cargados.
- [ ] Un pedido creado en el teléfono y uno bajado del servidor se ordenan correctamente por fecha de creación aunque PostgREST devuelva `+00:00`.
- [ ] Ajustar un pedido en la consola dejando un ítem en 0 y sincronizar hace desaparecer ese ítem del detalle en la app.
- [ ] Crear un pedido pendiente no cambia el stock mostrado en Catálogo hasta la siguiente sincronización.
- [ ] Ningún monto se muestra con más o menos de 2 decimales y `precio_visto_bs` llega a la BD exactamente como se vio en el catálogo local.
- [ ] `app-android/local.properties` no está en git; `local.properties.example`, `gradlew`, `gradlew.bat` y `gradle/wrapper/` sí.

## Decisiones

- **Sí:** una sola spec para RF-1 a RF-6. §8.6 ya detalla el módulo completo y sigue el precedente de la spec 01.
- **Sí:** toolchain Gradle 9.6.0 + AGP 9.4.1 + Kotlin 2.2.10 + KSP 2.3.6 en vez de Gradle 8.9 + AGP 8.7 + Kotlin 2.0 de §8.6. El JBR de Android Studio de la laptop es Java 25 y Gradle 8.9 no arranca sobre esa JVM (`IllegalArgumentException: 25.0.3`); no hay otro JDK instalado y la spec no instala nada. Gradle 9.6.0, AGP 9.4.1 y Kotlin 2.2.10 ya estaban cacheados por Android Studio, así que es la combinación que se sabe que corre aquí. AGP 9 compila Kotlin de forma integrada: no se aplica `org.jetbrains.kotlin.android`, y KSP ≥ 2.3.1 es el primero que acepta ese modo. Room sube a 2.7.x (soporte de KSP2) y la Compose BOM a 2025.05.00 (cacheada). Las demás librerías quedan como en §8.6, pineadas en `libs.versions.toml`; solo se sube un patch si hay un conflicto real al compilar, y se anota en el commit.
- **No:** instalar un JDK 17 aparte ni `org.gradle.java.home`. La verificación usa `JAVA_HOME` en el JBR y nada se instala.
- **Sí:** wrapper de Gradle 9.6.0 generado con la distribución 9.6 cacheada y commiteado. No hay `gradle` en el PATH ni se instala nada.
- **Sí:** nombres de archivos, clases y propiedades en inglés (`Graph`, `ProductEntity`, `NewOrderScreen`) aunque §8.6 los nombra en español. Misma regla que la spec 01; columnas y valores de estado siguen en español porque son el esquema aplicado.
- **Sí:** bajar los pedidos propios completos con ítems. Recupera historial al reinstalar y muestra los ajustes del supervisor; la consulta con embed ya la permite la RLS de vendedor.
- **No:** bajar solo estado y nota de pedidos existentes. Un pedido hecho desde otro teléfono o por SQL no se vería.
- **Sí:** un pedido rechazado por datos (`22023`) sigue `pendiente` con `lastError` visible y se reintenta en la siguiente sync. No se inventa un estado fuera de RF-5 ni se confunde con el rechazo del supervisor.
- **No:** estado local `error` ni marcarlo `rechazado`.
- **Sí:** la cantidad puede superar el stock local, con aviso. La copia local puede estar vieja; el servidor es la fuente de verdad (RF-6) y marcará Con problema.
- **No:** bloquear por stock local. Bloquearía ventas válidas con catálogo desactualizado.
- **Sí:** un pedido Pendiente de envío no se edita ni se borra. Igual que §7 para los observados; evita la carrera con el worker que puede estar enviándolo.
- **Sí:** la pestaña Pedidos lista todos los locales, más recientes primero y los pendientes arriba. Volumen bajo en el MVP; el historial bajado tiene valor para el vendedor.
- **Sí:** sincronización al entrar al Home, al confirmar un pedido, cada 15 min y con "Sincronizar ahora". El botón llama al repositorio directo para dar feedback inmediato; el worker cubre el segundo plano.
- **No:** sync al volver a primer plano ni Realtime en la app. Más tráfico y código sin ganancia para el MVP.
- **Sí:** `lastSync` por tabla igual al mayor `actualizado_en` recibido y filtro `gte`. Independiente del reloj del teléfono; una fila repetida se upsertea sin daño.
- **Sí:** en la bajada el servidor siempre gana, incluso sobre un `pendiente` local. Si el servidor lo tiene, ya lo recibió; mantener el estado local solo escondería la verdad.
- **Sí:** los ítems de un pedido bajado reemplazan a los locales. "Ajustar" puede eliminar ítems y un upsert por clave no los borraría.
- **Sí:** el stock local no se descuenta al crear un pedido pendiente. Evita descontar dos veces o mostrar un stock que el servidor no confirmó; el aviso de stock local ya orienta al vendedor.
- **Sí:** timestamps en UTC dentro de Room y conversión a La Paz solo en pantalla. Los creados en el teléfono y los de PostgREST llegan con offsets distintos y el orden por texto exige un formato único.
- **Sí:** `syncAll()` lee el perfil propio antes de todo. RLS devuelve 0 filas para un vendedor inactivo en vez de un error, y es la única forma de detectar la cuenta desactivada sin pedidos pendientes.
- **Sí:** clasificación de errores por HTTP (401/403 → sesión inválida, 400 → datos) y no por `errcode`. PostgREST mapea 42501 a 403 y 22023/P0001 a 400.
- **Sí:** Salir borra los pedidos locales y se bloquea con pendientes. Otro vendedor en el mismo teléfono no debe ver ni enviar pedidos ajenos (el worker enviaría con `vendedor_id = auth.uid()` del nuevo).
- **No:** `vendorId` en `OrderEntity` con filtros por vendedor en DAOs y worker. Más columnas y lógica para un caso que el bloqueo de Salir ya cubre.
- **Sí:** el worker espera `auth.awaitInitialization()` y no hace nada sin sesión. Evita llamadas con token aún no cargado desde el almacenamiento.
- **Sí:** cantidad entera. `pedido_items.cantidad` y `productos.stock` son `integer` en el esquema.
- **Sí:** `BigDecimal` en memoria, texto en Room y en DTOs, literal numérico sin comillas hacia la RPC. Nunca `Double`; la columna `numeric(10,2)` recibe el valor exacto.
- **Sí:** Room versión 1 con `fallbackToDestructiveMigration()`. Es un MVP; el catálogo se vuelve a bajar y los pedidos confirmados se recuperan del servidor.
- **Sí:** `SharedPreferences` para código y nombre del vendedor. Un par de strings no justifican Room ni DataStore.
- **Sí:** sin librería de navegación ni Hilt. Estado `screen` en `AppRoot` y pestaña en `HomeScreen`, como el `view` de la consola; `Graph` en `App.kt` como fija §8.6.
- **Sí:** un único test unitario (`MoneyTest.kt`) para los helpers de dinero. Es donde un error pasa desapercibido en pruebas manuales; el resto se verifica a mano según §8.7.
- **Sí:** `OneTimeWorkRequest` único con `KEEP` más `PeriodicWorkRequest` de 15 min. Un solo envío en curso por vez; `enviar_pedido` serializa reintentos del mismo uuid por si acaso.
- **No:** cambios en el backend. Si al implementar falta algo (por ejemplo una política), se anota en "Pendientes" y va a una spec de backend.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Combinación de versiones (AGP 9.4, Kotlin 2.2, KSP 2.3, supabase-kt 3.x, Ktor 3) no compila a la primera | Paso 1 cierra la build con las dependencias ya declaradas antes de escribir código; cualquier bump de patch se anota en el commit. |
| El wrapper necesita descargar las dependencias | Gradle 9.6.0 ya está cacheado; la laptop tiene red para las librerías y el primer `assembleDebug` tarda más. Se compila antes de levantar el segundo AVD por RAM (§6.2). |
| `precio_visto_bs` llega como `16.5` o como string a la RPC | Serializer propio que emite el literal con 2 decimales; el test unitario del paso 2 lo cubre. |
| La respuesta de `enviar_pedido` se pierde tras confirmar en el servidor (timeout) | El pedido sigue `pendiente` y el reintento con el mismo uuid devuelve el pedido existente (idempotencia RF-4); además `downloadOwnOrders()` lo trae por delta. |
| El worker no se dispara al quitar el modo avión en el emulador | `NetworkType.CONNECTED` reacciona al cambio de conectividad; si no, el periódico de 15 min o "Sincronizar ahora" lo envían. La prueba de §8.7 usa el botón si hace falta y lo anota. |
| Reloj del teléfono desfasado | `creado_en_dispositivo` es informativo; "pedidos del día" en la consola usa `recibido_en` del servidor y los deltas usan `actualizado_en` del servidor. |
| Sesión expirada sin red por varios días | `RefreshFailure` por red mantiene el Home y los pedidos pendientes; al volver la red el token se renueva. Solo `NotAuthenticated`, HTTP 401/403 o perfil sin fila expulsan. |
| El cliente de un pedido bajado no está en Room (falló la bajada de clientes) | `syncAll()` baja clientes antes que pedidos; si igual falta, la lista muestra el `clientId` y la siguiente sync lo completa. |
| Dos AVD a la vez agotan la RAM de la laptop | Compilar e instalar en A antes de levantar B (§6.2). |

## Lo que **no** entra en esta spec

- Cambios de esquema, RLS o RPC.
- Editar o borrar pedidos desde la app.
- Crear clientes, cambio o recuperación de contraseña.
- Realtime o notificaciones en la app.
- Filtro por fecha, reportes o exportación.
- Build de release, firma, Play Store.
- Migraciones de Room, tests instrumentados o de UI.

Cada uno de esos puntos, si llega, va en su propia spec.
