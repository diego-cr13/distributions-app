-- 0001_schema.sql
-- Tablas, checks, índices, trigger actualizado_en y publicación realtime.
-- Montos en bolivianos: numeric(10,2) precios/subtotales, numeric(12,2) totales. Nunca float.

-- ---------------------------------------------------------------------------
-- Tablas
-- ---------------------------------------------------------------------------

create table public.perfiles (
  id      uuid primary key references auth.users (id) on delete cascade,
  codigo  text not null unique,
  nombre  text not null,
  rol     text not null check (rol in ('vendedor', 'supervisor')),
  activo  boolean not null default true
);

create table public.clientes (
  id              uuid primary key default gen_random_uuid(),
  codigo          text not null unique,
  nombre          text not null,
  zona            text,
  ciudad          text,
  actualizado_en  timestamptz not null default now()
);

create table public.productos (
  id              uuid primary key default gen_random_uuid(),
  codigo          text not null unique,
  nombre          text not null,
  unidad          text not null,
  precio_bs       numeric(10,2) not null check (precio_bs > 0),
  stock           integer not null check (stock >= 0),
  actualizado_en  timestamptz not null default now()
);

create table public.pedidos (
  -- Generado en el teléfono; es la clave de idempotencia de enviar_pedido.
  id                     uuid primary key,
  vendedor_id            uuid not null references public.perfiles (id),
  cliente_id             uuid not null references public.clientes (id),
  estado                 text not null
                         check (estado in ('enviado', 'confirmado', 'con_problema', 'rechazado')),
  motivo                 text,
  total_bs               numeric(12,2) not null check (total_bs >= 0),
  creado_en_dispositivo  timestamptz not null,
  recibido_en            timestamptz not null default now(),
  resuelto_por           uuid references public.perfiles (id),
  resuelto_en            timestamptz,
  nota_supervisor        text,
  actualizado_en         timestamptz not null default now()
);

create table public.pedido_items (
  id                  bigserial primary key,
  pedido_id           uuid not null references public.pedidos (id) on delete cascade,
  producto_id         uuid not null references public.productos (id),
  -- Entera, igual que productos.stock.
  cantidad            integer not null check (cantidad > 0),
  precio_visto_bs     numeric(10,2) not null check (precio_visto_bs > 0),
  -- Se completa al confirmar (automático o por el supervisor).
  precio_aplicado_bs  numeric(10,2) check (precio_aplicado_bs > 0),
  subtotal_bs         numeric(10,2) not null check (subtotal_bs >= 0),
  problema            text check (problema in ('sin_stock', 'precio_cambiado')),
  unique (pedido_id, producto_id)
);

-- ---------------------------------------------------------------------------
-- Índices (sincronización incremental y pedidos del día)
-- ---------------------------------------------------------------------------

create index pedidos_vendedor_actualizado_idx on public.pedidos (vendedor_id, actualizado_en);
create index pedidos_recibido_idx            on public.pedidos (recibido_en);
create index pedidos_estado_idx              on public.pedidos (estado);
create index productos_actualizado_idx       on public.productos (actualizado_en);
create index clientes_actualizado_idx        on public.clientes (actualizado_en);
create index pedido_items_pedido_idx         on public.pedido_items (pedido_id);
create index pedido_items_producto_idx       on public.pedido_items (producto_id);

-- ---------------------------------------------------------------------------
-- Trigger actualizado_en
-- ---------------------------------------------------------------------------

create or replace function public.set_actualizado_en()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.actualizado_en := now();
  return new;
end;
$$;

create trigger clientes_actualizado_en
  before update on public.clientes
  for each row execute function public.set_actualizado_en();

create trigger productos_actualizado_en
  before update on public.productos
  for each row execute function public.set_actualizado_en();

create trigger pedidos_actualizado_en
  before update on public.pedidos
  for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------------
-- RLS activado desde ya (sin políticas = nadie accede vía API).
-- Las políticas se definen en 0002_rls.sql.
-- ---------------------------------------------------------------------------

alter table public.perfiles     enable row level security;
alter table public.clientes     enable row level security;
alter table public.productos    enable row level security;
alter table public.pedidos      enable row level security;
alter table public.pedido_items enable row level security;

-- ---------------------------------------------------------------------------
-- Realtime para la consola (RC-2)
-- ---------------------------------------------------------------------------

alter publication supabase_realtime add table public.pedidos;
