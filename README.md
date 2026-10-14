# Distribuidora de abarrotes – MVP

Reemplaza el proceso de pedidos en papel con tres piezas sobre un mismo proyecto Supabase en la nube:

- **Backend** Supabase (Postgres, Auth, Realtime, RPC): fuente de verdad de stock, precios y estado de cada pedido.
- **Consola web** (`consola-web/`) para supervisores: Vite + React + TypeScript + `supabase-js`.
- **App Android** (`app-android/`) para vendedores: Kotlin + Compose + Room + WorkManager + `supabase-kt`, con modo offline.

Requerimientos completos: [`docs/requirements.md`](docs/requirements.md). Specs implementadas: [`specs/01-consola-web.md`](specs/01-consola-web.md) y [`specs/02-app-android.md`](specs/02-app-android.md).

## Estructura

```
backend/
  migrations/          0001_schema.sql, 0002_rls.sql, 0003_rpc.sql
  seed.sql             productos y clientes (generado con scripts/gen_seed.mjs)
  datos_semilla/       CSV de vendedores, clientes y productos
  scripts/             generación de seed y alta de usuarios (service_role)
consola-web/           consola de supervisores (Vite + React)
app-android/           app de vendedores (Gradle, módulo app)
docs/                  requerimientos
specs/                 una spec por entrega, con decisiones y criterios de aceptación
```

## Prerequisitos

| Herramienta | Qué se necesita |
|-------------|-----------------|
| Node | v22 (`node` y `npm` en PATH). |
| Android Studio | Con su JBR (Java 25) en `C:\Program Files\Android\Android Studio\jbr`. No hace falta otro JDK ni Gradle en PATH. |
| Android SDK | Platform `android-35` y `adb` en PATH (`%LOCALAPPDATA%\Android\Sdk\platform-tools`). |
| Dos AVD | API 35, x86_64, 2 GB de RAM cada uno. Se usaron `Pixel_9a_V2` y `Pixel_9a`; sirven `Pixel_9_Pro_Vendedor-A` y `-B`. Crearlos en Device Manager de Android Studio. |
| Proyecto Supabase | Ya existe (`woqrkiceajvtptfcvdqt`), con esquema y seed aplicados. Solo hace falta su URL y la **anon key** (Dashboard → Project Settings → API). |

Todo corre contra la nube: no hay Supabase CLI ni Docker, y los emuladores se conectan directo a `https://<ref>.supabase.co` sin configurar `10.0.2.2` ni IPs locales.

## Configuración

Los archivos con secretos no se commitean; copiar cada `.example` y completar:

| Archivo | Variables | Quién lo necesita |
|---------|-----------|-------------------|
| `consola-web/.env.local` | `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` | Arranque diario |
| `app-android/local.properties` | `sdk.dir` (con barras `/`), `SUPABASE_URL`, `SUPABASE_ANON_KEY` | Arranque diario |
| `backend/scripts/.env` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SEED_PASSWORD` | Solo para reconstruir desde cero |

La app y la consola usan solo la **anon key**. La **service_role key** se usa únicamente en `backend/scripts/` y nunca va en un cliente.

## Usuarios de prueba

El seed crea 11 usuarios. El login pide el código y la app deriva el email (`V-01` → `v-01@distribuidora.test`). Contraseña: la de `SEED_PASSWORD` (por defecto `Distribuidora2026!`).

| Código | Rol | Entra en |
|--------|-----|----------|
| `S-01` | supervisor | Consola web (la app lo rechaza) |
| `V-01` … `V-10` | vendedor | App Android (la consola los rechaza) |

## Arranque diario

Asume el proyecto Supabase ya poblado. Son dos terminales: una para la consola y otra para la app.

### 1. Consola web

```
cd consola-web
npm install            # solo la primera vez
npm run dev            # http://localhost:5173
```

Entrar con `S-01`. `npm run build` compila con `tsc -b` y `npm run lint` corre oxlint.

### 2. App Android en dos AVD

Java no está en PATH; apuntar `JAVA_HOME` al JBR de Android Studio en cada terminal nueva:

```
# Git Bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
# PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Compilar **antes** de levantar el segundo emulador (dos AVD más Gradle agotan la RAM):

```
cd app-android
./gradlew assembleDebug            # primera vez: descarga dependencias, tarda varios minutos
./gradlew testDebugUnitTest        # tests de dinero y tiempo (app/src/test)
```

Levantar el AVD A, instalar, levantar el AVD B, instalar. Los seriales suelen ser `emulator-5554` y `emulator-5556` (`adb devices` los lista; `adb -s <serial> emu avd name` dice qué AVD ocupa cada uno):

```
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk   # AVD A → V-01
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk   # AVD B → V-02
```

Modo avión por AVD, independiente del otro:

```
adb -s emulator-5554 shell cmd connectivity airplane-mode enable
adb -s emulator-5554 shell cmd connectivity airplane-mode disable
```

Sesión y base Room son por dispositivo; el único punto compartido es Supabase.

## Reconstruir desde cero

Solo si se parte de un proyecto Supabase vacío (el actual ya está listo). Hace falta la **service_role key** en `backend/scripts/.env`.

1. **Esquema**: aplicar en orden `backend/migrations/0001_schema.sql`, `0002_rls.sql`, `0003_rpc.sql`. Se hizo con el MCP de Supabase (`apply_migration` con el archivo tal cual); también se pueden pegar en el SQL Editor del dashboard. Un cambio de esquema va en una migración nueva (`0004_...`), nunca editando las aplicadas.
2. **Seed de catálogo**:
   ```
   cd backend/scripts
   npm install
   node gen_seed.mjs          # CSV de datos_semilla/ → backend/seed.sql (no editar seed.sql a mano)
   ```
   Aplicar `backend/seed.sql` (MCP `execute_sql` o SQL Editor): 50 productos y 30 clientes.
3. **Usuarios**: `node seed_usuarios.mjs` crea o actualiza los 11 usuarios en Auth y sus filas en `perfiles`. Es repetible.
4. **Clientes**: poner la URL y la anon key del proyecto nuevo en `consola-web/.env.local` y `app-android/local.properties`. Si el esquema cambió, regenerar `consola-web/src/lib/database.types.ts` con el MCP (`generate_typescript_types`).
5. **Verificar**: en el dashboard hay 50 productos, 30 clientes, 11 usuarios en Auth y 11 perfiles.

## Guión de demo

Con la consola abierta como `S-01`, `V-01` en el AVD A y `V-02` en el AVD B:

1. **Offline (RF-4)**: A en modo avión. Crear dos pedidos; aparecen "Pendiente de envío" y la pestaña Pedidos marca 2. Cerrar y abrir la app: siguen ahí y no pide login.
2. **Sync automática**: quitar el modo avión en A. Sin tocar nada, los pedidos pasan a Confirmado y aparecen en "Pedidos del día" de la consola sin recargar. Si el worker tarda, "Sincronizar ahora".
3. **Precio cambiado**: A en modo avión vende `P-0001`. En la consola cambiar el precio de `P-0001`. Reconectar A: el pedido queda Con problema (`precio_cambiado`) y entra a la bandeja Problemas.
4. **Carrera por stock (RF-6)**: A y B en modo avión, cada uno pide 12 unidades de `P-0004` (stock 12). Reconectar ambos: uno Confirmado, el otro Con problema (`sin_stock`). El stock en Catálogo bajó una sola vez.
5. **Resolución**: en Problemas, Rechazar el de stock con una nota y Ajustar el de precio al precio vigente. En cada AVD, "Sincronizar ahora": el detalle muestra Rechazado con la nota, y el ajustado muestra Confirmado con los precios nuevos.

Los criterios de aceptación completos están al final de cada spec.

## Decisiones y por qué

Las que afectan al arranque y al stack. El detalle de cada spec está en su sección "Decisiones".

- **Supabase en la nube, sin CLI ni Docker.** No hay nada que instalar, los dos emuladores conectan a la misma URL y comparten el backend, que es lo que exige la prueba de RF-6. El esquema se aplica con el MCP de Supabase a partir de los archivos del repo.
- **Nadie escribe directo en `pedidos`.** Todo pasa por las RPC `enviar_pedido` (app, idempotente por uuid generado en el teléfono) y `resolver_pedido` (consola). Así el servidor decide stock y precio en una sola transacción y la carrera entre vendedores offline se resuelve con bloqueos de fila.
- **anon key en los clientes, service_role solo en `backend/scripts/`.** RLS protege los datos por rol; la service_role existe para crear usuarios y no debe salir de la laptop.
- **Español en toda la UI; inglés en el código.** Los usuarios son vendedores y supervisores locales. Archivos, clases y commits van en inglés; las columnas y los valores de estado quedan en español porque son el esquema ya aplicado.
- **Vite + React + TypeScript sin router ni state manager.** Tres vistas y un día de trabajo: un estado `view` basta. Realtime refetchea la consulta completa del día en vez de parchar estado en memoria.
- **Kotlin + Compose + Room + WorkManager sin Hilt ni librería de navegación.** Inyección manual con un singleton en `Application`, como fija el requerimiento §8.6. El worker sube pendientes al volver la red y luego baja catálogo, clientes y pedidos propios por delta sobre `actualizado_en`.
- **Gradle 9.6.0 + AGP 9.4.1 + Kotlin 2.2.10 en vez de Gradle 8.9 + AGP 8.7.** El único Java de la laptop es el JBR de Android Studio (Java 25) y Gradle 8.9 no arranca sobre esa JVM. No se instaló otro JDK: se usó la combinación que Android Studio ya tenía cacheada.
- **Wrapper de Gradle commiteado.** No hay `gradle` en PATH y así cualquiera compila con `./gradlew` y la misma versión.
- **Dinero nunca en float.** `numeric(10,2)` en la BD, `BigDecimal` en Android (texto en Room), centavos enteros en la consola. Formato `Bs 16.50`.
- **El servidor siempre gana.** La app no descuenta stock local ni bloquea por stock desactualizado; si el precio o el stock cambiaron, el pedido queda Con problema y decide el supervisor.
- **Casi sin tests automatizados.** Solo `MoneyTest` y `TimeTest` en la app, donde un error pasa desapercibido a mano. El resto se verifica con el guión de demo contra el proyecto en la nube.
- **Sin despliegue ni build de release.** La consola corre con `npm run dev` y la app como `assembleDebug` en emuladores; publicar queda fuera del MVP.

## Pendientes

- **Consola web** (`specs/01-consola-web.md`): sin pendientes. Fuera de alcance por decisión: historial de días anteriores, reportes, alta de productos, gestión de clientes/vendedores, versión móvil, tests automatizados y despliegue.
- **App Android** (`specs/02-app-android.md`): fuera de alcance por decisión: editar o borrar pedidos desde la app, alta de clientes, cambio o recuperación de contraseña, Realtime y notificaciones, filtros o reportes en Pedidos, build de release y firma, migraciones de Room (destructiva en el MVP), tests instrumentados o de UI. Pendientes observados: no hay forma de provocar desde la app un rechazo por datos de `enviar_pedido` (la app valida lo mismo que la RPC), así que ese camino (`lastError` en la lista) solo se verificó por lectura del código; el mensaje `motivo` del servidor se muestra tal cual (`sin_stock: P-0004`); al abrir la app en frío el emulador tarda unos segundos en cargar la sesión y mientras tanto la pantalla queda en blanco.
- **Backend**: el advisor de seguridad de Supabase reporta `public.rls_auto_enable()` ejecutable por `anon`; no está en `backend/migrations/`. Revisar su origen en la próxima spec de backend.
