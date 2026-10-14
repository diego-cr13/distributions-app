-- 0002_rls.sql
-- Funciones auxiliares de rol, políticas RLS y permisos.
-- Perfiles con activo = false no pasan ninguna política.
-- Los permisos de ejecución de enviar_pedido / resolver_pedido van en 0003_rpc.sql,
-- junto a las funciones.

-- ---------------------------------------------------------------------------
-- Funciones auxiliares
-- security definer: leen perfiles sin depender de sus propias políticas.
-- ---------------------------------------------------------------------------

create or replace function public.es_supervisor()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.perfiles
    where id = auth.uid() and rol = 'supervisor' and activo
  );
$$;

create or replace function public.es_vendedor_activo()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.perfiles
    where id = auth.uid() and rol = 'vendedor' and activo
  );
$$;

-- ---------------------------------------------------------------------------
-- Políticas
-- ---------------------------------------------------------------------------

-- perfiles: el propio (si está activo) o cualquiera si es supervisor.
create policy perfiles_select on public.perfiles
  for select to authenticated
  using ((id = (select auth.uid()) and activo) or (select public.es_supervisor()));

-- clientes: lectura para vendedor activo o supervisor.
create policy clientes_select on public.clientes
  for select to authenticated
  using ((select public.es_vendedor_activo()) or (select public.es_supervisor()));

-- productos: lectura para vendedor activo o supervisor; edición solo supervisor (RC-3).
create policy productos_select on public.productos
  for select to authenticated
  using ((select public.es_vendedor_activo()) or (select public.es_supervisor()));

create policy productos_update on public.productos
  for update to authenticated
  using ((select public.es_supervisor()))
  with check ((select public.es_supervisor()));

-- pedidos: los propios del vendedor activo, o todos si es supervisor.
-- Sin políticas de insert/update/delete: todo pasa por las RPC.
create policy pedidos_select on public.pedidos
  for select to authenticated
  using (
    (vendedor_id = (select auth.uid()) and (select public.es_vendedor_activo()))
    or (select public.es_supervisor())
  );

-- pedido_items: los de pedidos que el usuario puede ver.
create policy pedido_items_select on public.pedido_items
  for select to authenticated
  using (
    (select public.es_supervisor())
    or exists (
      select 1 from public.pedidos p
      where p.id = pedido_id
        and p.vendedor_id = (select auth.uid())
        and (select public.es_vendedor_activo())
    )
  );

-- ---------------------------------------------------------------------------
-- Permisos de tablas
-- Supabase da todo a anon/authenticated por defecto; se deja solo lo necesario.
-- ---------------------------------------------------------------------------

revoke all on public.perfiles, public.clientes, public.productos,
              public.pedidos, public.pedido_items
  from anon, authenticated;

grant select on public.perfiles, public.clientes, public.productos,
                public.pedidos, public.pedido_items
  to authenticated;

-- El supervisor solo puede cambiar precio y stock (no código, nombre, etc.).
grant update (precio_bs, stock) on public.productos to authenticated;

revoke all on sequence public.pedido_items_id_seq from anon, authenticated;

-- ---------------------------------------------------------------------------
-- Permisos de funciones
-- Las auxiliares las evalúan las políticas con el rol del usuario: authenticated
-- necesita execute. anon no.
-- ---------------------------------------------------------------------------

revoke execute on function public.es_supervisor()      from public, anon;
revoke execute on function public.es_vendedor_activo() from public, anon;
revoke execute on function public.set_actualizado_en() from public, anon, authenticated;

grant execute on function public.es_supervisor()      to authenticated;
grant execute on function public.es_vendedor_activo() to authenticated;
