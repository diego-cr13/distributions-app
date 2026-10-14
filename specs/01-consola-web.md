# SPEC 01 — Consola web de supervisores

> **Estado:** Implementado
> **Depende de:** ninguna spec previa (el backend de `backend/migrations/` y el seed ya están aplicados en el proyecto Supabase)
> **Fecha:** 2026-10-14
> **Objetivo:** Construir en `consola-web/` la consola Vite + React + TypeScript con la que un supervisor entra con su cuenta, ve en tiempo real los pedidos del día, edita precio y stock del catálogo y resuelve los pedidos con problema.

## Por qué existe esta spec

`docs/requirements.md` §8.5 ya fija stack, vistas y archivos de la consola, pero deja abiertas decisiones de comportamiento (qué refresca Realtime, qué estados se listan, cómo se edita el catálogo, qué se precarga al ajustar, si la nota es obligatoria). Esta spec cierra esas decisiones y define cómo verificar la consola **antes** de que exista la app Android, generando pedidos por SQL.

## Alcance

**Dentro:**

- Proyecto Vite `react-ts` en `consola-web/`, con `@supabase/supabase-js`, sin router ni backend propio, CSS plano.
- Tipos del esquema generados con el MCP de Supabase en `src/lib/database.types.ts`.
- RC-1: login con código de supervisor y contraseña; rechazo de quien no tenga rol `supervisor`; sesión persistida en `localStorage`; botón Salir.
- RC-2: lista de pedidos del día (`America/La_Paz`) con hora, vendedor, cliente, ítems, total y estado; actualización por Realtime; filtro y agrupación por vendedor y por cliente; total del día con desglose por vendedor.
- RC-3: catálogo completo con edición de `precio_bs` y `stock` por fila.
- RC-4: bandeja de todos los pedidos `con_problema` con motivo por ítem y acciones Aprobar, Ajustar y Rechazar vía `rpc('resolver_pedido')`.
- Toda la UI, mensajes de error y etiquetas de estado en español.
- `consola-web/.env.example` y actualización mínima de `README.md` si cambia la puesta en marcha.

**Fuera de alcance (para otras specs):**

- Cualquier cambio en el backend (migraciones, RLS, RPC). Si la consola necesita algo que el esquema no da, se anota y se hace en una spec de backend.
- Gestión de clientes, vendedores o perfiles (§7: no en el MVP).
- Historial de pedidos de días anteriores, reportes o exportación.
- Alta de productos o edición de nombre/unidad/código.
- Modo offline, PWA, responsive para móvil.
- Suite de tests automatizados (el proyecto no la tiene; la verificación es manual).
- Despliegue de la consola (solo `npm run dev` y `npm run build` locales).

## Modelo de datos

La consola no crea tablas ni columnas. Trabaja sobre `perfiles`, `productos`, `clientes`, `pedidos` y `pedido_items`, y llama a `resolver_pedido`. Los nombres de columnas y valores internos de estado quedan en español porque son el esquema ya aplicado.

Tipos propios de la consola (en inglés, como todo el código nuevo), en `src/lib/orders.ts`:

```ts
export type OrderStatus = 'enviado' | 'confirmado' | 'con_problema' | 'rechazado';
export type ItemProblem = 'sin_stock' | 'precio_cambiado';

export interface OrderItem {
  producto_id: string;
  cantidad: number;
  precio_visto_bs: number;
  precio_aplicado_bs: number | null;
  subtotal_bs: number;
  problema: ItemProblem | null;
  producto: { codigo: string; nombre: string; unidad: string; precio_bs: number; stock: number };
}

export interface Order {
  id: string;
  estado: OrderStatus;
  motivo: string | null;
  total_bs: number;
  recibido_en: string;          // ISO, se muestra en hora de La Paz
  nota_supervisor: string | null;
  vendedor: { codigo: string; nombre: string };
  cliente: { codigo: string; nombre: string };
  items: OrderItem[];
}
```

Una sola consulta compartida por Pedidos y Problemas, con embeds de PostgREST. Como `pedidos` tiene dos claves foráneas a `perfiles` (`vendedor_id` y `resuelto_por`), el embed del vendedor nombra la constraint: `vendedor:perfiles!pedidos_vendedor_id_fkey(codigo, nombre)`.

Estado de la aplicación en `App.tsx`:

```ts
type View = 'orders' | 'catalog' | 'problems';
// session: Session | null            (supabase-js)
// profile: { codigo, nombre } | null (solo si rol === 'supervisor')
// view: View                          (por defecto 'orders')
// ordersVersion: number               (se incrementa con cada evento Realtime; Pedidos y Problemas refetchean al cambiar)
```

Dinero (`src/lib/money.ts`): los montos llegan de PostgREST como número JSON con 2 decimales. Se convierten **una vez** a centavos enteros con `toCents(n) = Math.round(n * 100)`; todas las sumas (total del día, desglose por vendedor, total previsto al ajustar) se hacen sobre enteros y se formatean con `formatBs(cents)` → `Bs 16.50`. Los inputs de precio se validan con `^\d+(\.\d{1,2})?$` antes de enviarse.

Día (`src/lib/day.ts`): `startOfDayLaPaz(now = new Date()): string` devuelve el ISO de las 00:00 del día actual en UTC-4 fijo (sin horario de verano), que se usa en `recibido_en >= …`. La hora se muestra con `toLocaleTimeString('es-BO', { timeZone: 'America/La_Paz', hour: '2-digit', minute: '2-digit' })`.

Etiquetas (`src/lib/labels.ts`): mapa de estado interno → etiqueta RF-5 (`enviado` → "Enviado", `confirmado` → "Confirmado", `con_problema` → "Con problema", `rechazado` → "Rechazado") y de problema → texto (`sin_stock` → "Sin stock", `precio_cambiado` → "Precio cambiado").

## Archivos

```
consola-web/
  package.json, vite.config.ts, tsconfig*.json, index.html   scaffold de Vite react-ts
  .env.example                                               VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY
  src/main.tsx
  src/index.css                                              CSS plano: layout, tabla, badges de estado, formularios
  src/App.tsx                                                sesión, perfil, navegación, canal Realtime, vista activa
  src/lib/supabase.ts                                        createClient<Database>(VITE_*)
  src/lib/database.types.ts                                  generado con el MCP (generate_typescript_types)
  src/lib/money.ts                                           toCents, formatBs, isValidPrice
  src/lib/day.ts                                             startOfDayLaPaz, formatTimeLaPaz
  src/lib/labels.ts                                          etiquetas en español de estado y problema
  src/lib/orders.ts                                          tipos Order/OrderItem y fetchOrders({ since?, status? })
  src/components/Login.tsx                                   RC-1
  src/components/TodayOrders.tsx                             RC-2
  src/components/Catalog.tsx                                 RC-3
  src/components/ProblemOrders.tsx                           RC-4: lista, Aprobar, Rechazar
  src/components/AdjustOrderForm.tsx                         RC-4: formulario de Ajustar
```

## Plan de implementación

Cada paso deja la consola arrancando con `npm run dev` y compilando con `npm run build` (incluye `tsc`).

1. **Scaffold.** `npm create vite@latest consola-web -- --template react-ts`, `npm install @supabase/supabase-js`, `.env.example`, `src/lib/supabase.ts`. Generar `src/lib/database.types.ts` con `generate_typescript_types` del MCP. `App.tsx` muestra solo el título "Consola de supervisores". Prueba: `npm run dev` abre la página sin errores en consola del navegador.
2. **Helpers.** `money.ts`, `day.ts`, `labels.ts`. Prueba: `formatBs(1650)` → `Bs 16.50`; `startOfDayLaPaz(new Date('2026-10-14T03:30:00Z'))` → `2026-10-13T00:00:00-04:00` (a las 03:30 UTC todavía es 13 de octubre en La Paz).
3. **Login y sesión (RC-1).** `Login.tsx` con código (`S-01`) y contraseña; deriva el email `codigo.toLowerCase() + '@distribuidora.test'`; `signInWithPassword`; lee `perfiles` propio; si `rol !== 'supervisor'` o `activo === false` → `signOut()` y mensaje "Solo un supervisor puede entrar a la consola". `App.tsx` escucha `onAuthStateChange`, recupera la sesión al cargar y vuelve a validar el rol; cabecera con nombre del supervisor y botón Salir. Prueba: `S-01` entra, `V-01` ve el mensaje y no entra, recargar mantiene la sesión.
4. **Consulta de pedidos.** `orders.ts` con `fetchOrders` (select con embeds de vendedor, cliente e ítems con producto, orden `recibido_en` desc). `TodayOrders.tsx` lista los pedidos del día en una tabla: hora, vendedor, cliente, ítems ("3 × Arroz Grano de Oro 1 kg"), total y badge de estado; botón "Actualizar". Datos de prueba: con `execute_sql` simular a `V-01` y llamar `enviar_pedido` con un pedido válido y otro con cantidad mayor al stock (§8.4). Prueba: ambos aparecen con Confirmado y Con problema.
5. **Realtime.** En `App.tsx`, un canal `postgres_changes` sobre `public.pedidos` (insert y update) que incrementa `ordersVersion`; `TodayOrders` y `ProblemOrders` refetchean cuando cambia. Se suscribe al tener sesión y se elimina al salir. Prueba: insertar un pedido por SQL con la consola abierta → aparece sin recargar.
6. **Filtros, agrupación y totales (RC-2).** Selects "Vendedor" y "Cliente" con opciones derivadas de los pedidos cargados; selector "Agrupar por: Ninguno / Vendedor / Cliente" que inserta filas de cabecera con subtotal; bloque "Total del día" con cantidad de pedidos y monto, más tabla de desglose por vendedor. Los totales excluyen los pedidos `rechazado` y los `con_problema` (solo se suma lo confirmado). Prueba: con dos pedidos de vendedores distintos los filtros y el desglose coinciden con lo esperado.
7. **Catálogo (RC-3).** `Catalog.tsx` lista los 50 productos (código, nombre, unidad, precio, stock) con botón Editar por fila; en edición, inputs de precio y stock con Guardar / Cancelar; valida precio > 0 con hasta 2 decimales y stock entero ≥ 0; `update productos set precio_bs, stock where id` con `.select().single()` para refrescar la fila; error del backend mostrado en la fila. Prueba: cambiar el precio de `P-0001` y verlo en el dashboard de Supabase.
8. **Bandeja de problemas: Aprobar y Rechazar (RC-4).** `ProblemOrders.tsx` lista todos los `con_problema` (sin filtro de fecha, `recibido_en` asc) con motivo general, y por ítem: cantidad, precio visto, precio vigente, stock actual y problema. Campo de nota por pedido. Aprobar llama `rpc('resolver_pedido', { p_id, p_accion: 'aprobar', p_nota })`; Rechazar exige nota no vacía y llama con `'rechazar'`. El mensaje de error de la RPC se muestra tal cual junto al pedido. Prueba: aprobar el pedido sin stock muestra "Stock insuficiente: …"; subir el stock desde Catálogo y aprobar → Confirmado y stock descontado; rechazar sin nota no envía nada.
9. **Ajustar (RC-4).** `AdjustOrderForm.tsx` abre bajo el pedido con una fila por ítem: cantidad precargada, precio precargado con el **precio vigente**, total previsto en vivo (centavos); cantidad 0 elimina el ítem; si todos quedan en 0 se bloquea con "Usa Rechazar". Confirmar llama `rpc('resolver_pedido', { p_id, p_accion: 'ajustar', p_items_ajustados: [{ producto_id, cantidad, precio_bs }], p_nota })`. Prueba: ajustar un pedido `precio_cambiado` con el precio vigente → Confirmado, `precio_aplicado_bs` igual al vigente, total recalculado.
10. **Acabado.** Estilos en `index.css` (badges por estado: Confirmado verde, Con problema ámbar, Rechazado gris, Enviado azul), estados de carga y de error en las tres vistas, contador de pedidos con problema en la pestaña "Problemas". `npm run build` sin errores de TypeScript. Si algo quedó fuera, anotarlo en "Pendientes" de `README.md`.

## Criterios de aceptación

- [x] `npm run dev` arranca y `npm run build` termina sin errores de TypeScript.
- [x] `S-01` con la contraseña del seed entra y ve su nombre en la cabecera.
- [x] `V-01` con contraseña correcta ve "Solo un supervisor puede entrar a la consola" y no queda con sesión.
- [x] Recargar la pestaña mantiene la sesión; Salir vuelve al login.
- [x] Un pedido creado por SQL (simulando a `V-01`) aparece en Pedidos del día sin recargar la página.
- [x] Cada pedido muestra hora en La Paz, código y nombre del vendedor, cliente, ítems con cantidad, total `Bs 0.00` y etiqueta exacta de RF-5.
- [x] Un pedido con `recibido_en` del día anterior en La Paz no aparece en Pedidos del día.
- [x] Filtrar por vendedor o por cliente deja solo los pedidos de esa opción; agrupar inserta cabeceras con subtotal.
- [x] "Total del día" muestra cantidad de pedidos confirmados y su monto, y el desglose por vendedor suma lo mismo.
- [x] En Catálogo, editar `P-0001` a otro precio y guardar actualiza la fila y el valor se ve en el dashboard de Supabase.
- [x] Guardar un precio con 3 decimales, precio 0 o stock negativo se bloquea en la UI con mensaje en español.
- [x] La bandeja Problemas lista pedidos `con_problema` de cualquier fecha y muestra "Sin stock" o "Precio cambiado" por ítem.
- [x] Aprobar un pedido sin stock suficiente muestra el mensaje de la RPC ("Stock insuficiente: …") y el pedido sigue en la bandeja.
- [x] Aprobar con stock suficiente pasa el pedido a Confirmado, lo quita de la bandeja y descuenta el stock en Catálogo.
- [x] Rechazar con la nota vacía no llama a la RPC; con nota, el pedido pasa a Rechazado y la nota queda en `nota_supervisor`.
- [x] Ajustar precarga el precio vigente, recalcula el total en vivo y al confirmar el pedido queda Confirmado con `precio_aplicado_bs` igual al enviado.
- [x] Todo texto visible está en español y ningún monto se muestra con más o menos de 2 decimales.
- [x] `consola-web/.env.local` no está en git y `.env.example` sí.

**Cómo se verificó (2026-10-14):** `npm run build` y `npm run lint` sin errores. Los helpers (`formatBs`, `toCents`, `isValidPrice`, `startOfDayLaPaz`) se comprobaron con un script Node sobre los archivos de `src/lib/`. Lo que toca al backend se corrió de extremo a extremo contra el proyecto en la nube con `supabase-js` y la **anon key**, repitiendo exactamente las llamadas de la consola (login de `S-01` y rechazo de `V-01`, el `select` con embeds de `orders.ts`, el `update` de `Catalog.tsx`, `rpc('resolver_pedido')` con aprobar / rechazar / ajustar y el canal Realtime de `App.tsx`, que recibió los eventos INSERT y UPDATE). Los pedidos de prueba quedaron en la BD con la nota "Prueba de cierre spec 01" y sirven como datos para la app Android. Los criterios de pura interfaz (recarga de pestaña, filtros y agrupación, validaciones bloqueadas en la UI, textos en español) se verificaron leyendo `TodayOrders.tsx`, `Catalog.tsx`, `ProblemOrders.tsx` y `AdjustOrderForm.tsx`; el recorrido en navegador queda como prueba manual de §8.7 paso 2.

## Decisiones

- **Sí:** una sola spec para RC-1 a RC-4. §8.5 ya detalla las cuatro vistas, es una sola área del sistema y la tarea es de un día.
- **Sí:** refetch completo de la consulta del día ante cada evento Realtime. Volumen diario bajo, siempre consistente, mucho menos código que parchar estado.
- **No:** parchar el estado en memoria con el payload del evento. El payload no trae ítems ni joins y abre inconsistencias.
- **Sí:** un único canal Realtime en `App.tsx` que incrementa `ordersVersion`; Pedidos y Problemas lo consumen. El catálogo solo se refresca tras guardar o al entrar a la vista.
- **Sí:** botón "Actualizar" en Pedidos y Problemas como respaldo si el canal Realtime se cae.
- **Sí:** la lista del día muestra los cuatro estados, incluido Rechazado. El supervisor ve en el día lo que decidió; `enviado` casi nunca persiste porque la RPC decide en la misma transacción.
- **Sí:** la bandeja muestra todos los `con_problema` sin filtro de fecha. Un pedido observado ayer sigue esperando decisión.
- **Sí:** el total del día suma solo pedidos `confirmado`. Lo rechazado no se entrega y lo observado aún no es venta.
- **Sí:** edición del catálogo con botón Editar por fila y Guardar / Cancelar. Un solo `update` por fila, sin escrituras accidentales.
- **No:** guardar al perder el foco. Dispara escrituras por cambios no deseados.
- **Sí:** Ajustar precarga el precio vigente. §7 define Ajustar como la vía para usar el precio actual; Aprobar conserva el visto.
- **Sí:** nota obligatoria solo en Rechazar. El vendedor necesita el motivo en la app; en Aprobar y Ajustar es opcional.
- **Sí:** sesión persistida en `localStorage` (comportamiento por defecto de supabase-js) y rol re-verificado en cada carga.
- **Sí:** tipos generados con el MCP en `src/lib/database.types.ts` y commiteados. Consultas y RPC tipadas sin costo; se regeneran si cambia el esquema.
- **Sí:** opciones de filtros derivadas de los pedidos cargados. Cero consultas extra.
- **Sí:** montos convertidos una vez a centavos enteros y sumados como enteros; nunca se suman floats.
- **Sí:** nombres de archivos, componentes, tipos y variables en inglés (`TodayOrders.tsx`, `money.ts`), aunque §8.5 los nombra en español. La convención de `CLAUDE.md` es obligatoria y posterior; las columnas del esquema y los valores de estado siguen en español porque ya están aplicados.
- **Sí:** pedidos de prueba generados por SQL con `execute_sql` simulando a `V-01` (§8.4). No se escribe un script nuevo.
- **No:** script Node de pedidos de prueba en `backend/scripts/`. Código fuera de la consola para algo que la app Android hará después.
- **No:** router, librería de componentes, state manager. Un estado `view` en `App.tsx` basta para tres vistas.
- **No:** cambios en el backend. Todo lo que la consola necesita ya existe (RLS de supervisor, grant de columnas, `resolver_pedido`, publicación Realtime).

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Realtime no entrega eventos (RLS exige JWT en el canal, o el canal se desconecta) | supabase-js pasa el token al canal automáticamente al iniciar sesión. Si falla, el botón "Actualizar" y el refetch al cambiar de vista mantienen la consola usable. |
| Embed ambiguo de `perfiles` por dos claves foráneas en `pedidos` | El select nombra la constraint `pedidos_vendedor_id_fkey`. Se verifica en el paso 4 antes de construir la UI. |
| Cambio de día a medianoche de La Paz con la consola abierta | `startOfDayLaPaz` se recalcula en cada refetch; el siguiente evento o "Actualizar" ya filtra por el día nuevo. |
| Precio enviado como float desde JS | Validación por regex de 2 decimales antes de enviar; un número con 2 decimales se serializa exacto en JSON y la columna es `numeric(10,2)`. Las sumas en la UI son en centavos enteros. |
| El supervisor aprueba mientras otro pedido consumió el stock | La RPC bloquea productos y falla con "Stock insuficiente: …"; la consola muestra el mensaje y refetchea. |

## Lo que **no** entra en esta spec

- Cambios de esquema, RLS o RPC.
- Gestión de clientes, vendedores o perfiles.
- Historial de días anteriores, reportes, exportación.
- Alta de productos o edición de campos distintos de precio y stock.
- Modo offline, PWA, versión móvil.
- Tests automatizados y despliegue.

Cada uno de esos puntos, si llega, va en su propia spec.
