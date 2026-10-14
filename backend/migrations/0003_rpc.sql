-- 0003_rpc.sql
-- enviar_pedido (app) y resolver_pedido (consola).
-- security definer: cada llamada corre en una sola transacción y es la única
-- vía para escribir pedidos (RLS no da insert/update a nadie).

-- ---------------------------------------------------------------------------
-- Esquema privado: helpers que no se exponen por la API (PostgREST solo ve public).
-- ---------------------------------------------------------------------------

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- Respuesta común de ambas RPC:
-- {id, estado, motivo, total_bs, items: [{producto_id, problema, precio_vigente_bs}]}
create or replace function private.respuesta_pedido(p_id uuid)
returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
    'id',       p.id,
    'estado',   p.estado,
    'motivo',   p.motivo,
    'total_bs', p.total_bs,
    'items', coalesce((
      select jsonb_agg(jsonb_build_object(
               'producto_id',       i.producto_id,
               'problema',          i.problema,
               'precio_vigente_bs', pr.precio_bs
             ) order by i.id)
      from public.pedido_items i
      join public.productos pr on pr.id = i.producto_id
      where i.pedido_id = p.id
    ), '[]'::jsonb)
  )
  from public.pedidos p
  where p.id = p_id;
$$;

revoke execute on function private.respuesta_pedido(uuid) from public;

-- ---------------------------------------------------------------------------
-- enviar_pedido
-- p_items = [{producto_id, cantidad, precio_visto_bs}]
-- ---------------------------------------------------------------------------

create or replace function public.enviar_pedido(
  p_id         uuid,
  p_cliente_id uuid,
  p_items      jsonb,
  p_creado_en  timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid        uuid := auth.uid();
  v_ids        uuid[];
  v_n_items    int;
  v_n_prod     int;
  v_problemas  int;
  v_total      numeric(12,2);
  v_motivo     text;
begin
  if not public.es_vendedor_activo() then
    raise exception 'Solo un vendedor activo puede enviar pedidos' using errcode = '42501';
  end if;

  if p_id is null or p_cliente_id is null or p_creado_en is null then
    raise exception 'Faltan datos del pedido (id, cliente o fecha)' using errcode = '22023';
  end if;

  -- Serializa reintentos simultáneos del mismo uuid.
  perform pg_advisory_xact_lock(hashtextextended(p_id::text, 0));

  -- 1. Idempotencia (RF-4): si ya existe, se devuelve tal cual.
  if exists (select 1 from public.pedidos where id = p_id) then
    if not exists (select 1 from public.pedidos where id = p_id and vendedor_id = v_uid) then
      raise exception 'El pedido % pertenece a otro vendedor', p_id using errcode = '42501';
    end if;
    return private.respuesta_pedido(p_id);
  end if;

  -- Validaciones de entrada.
  if not exists (select 1 from public.clientes where id = p_cliente_id) then
    raise exception 'El cliente no existe' using errcode = '22023';
  end if;

  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'El pedido no tiene productos' using errcode = '22023';
  end if;

  if exists (
    select 1
    from jsonb_to_recordset(p_items) as x(producto_id uuid, cantidad int, precio_visto_bs numeric)
    where x.producto_id is null
       or x.cantidad is null or x.cantidad <= 0
       or x.precio_visto_bs is null or x.precio_visto_bs <= 0
       or x.precio_visto_bs <> round(x.precio_visto_bs, 2)
  ) then
    raise exception 'Producto con cantidad o precio inválido (cantidad > 0, precio > 0 con 2 decimales)'
      using errcode = '22023';
  end if;

  select array_agg(distinct x.producto_id order by x.producto_id), count(*)
    into v_ids, v_n_items
  from jsonb_to_recordset(p_items) as x(producto_id uuid);

  if cardinality(v_ids) <> v_n_items then
    raise exception 'Un producto aparece más de una vez en el pedido' using errcode = '22023';
  end if;

  -- 2. Bloqueo de productos en orden de id (evita deadlocks; RF-6).
  select count(*) into v_n_prod
  from (
    select id from public.productos where id = any (v_ids) order by id for update
  ) bloqueados;

  if v_n_prod <> cardinality(v_ids) then
    raise exception 'El pedido incluye productos que no existen' using errcode = '22023';
  end if;

  -- 3. Validación por ítem contra el stock y precio vigentes.
  insert into public.pedidos (id, vendedor_id, cliente_id, estado, total_bs, creado_en_dispositivo)
  values (p_id, v_uid, p_cliente_id, 'enviado', 0, p_creado_en);

  insert into public.pedido_items
    (pedido_id, producto_id, cantidad, precio_visto_bs, subtotal_bs, problema)
  select
    p_id,
    x.producto_id,
    x.cantidad,
    x.precio_visto_bs,
    round(x.cantidad * x.precio_visto_bs, 2),
    case
      when pr.stock < x.cantidad              then 'sin_stock'
      when pr.precio_bs <> x.precio_visto_bs  then 'precio_cambiado'
    end
  from jsonb_to_recordset(p_items) as x(producto_id uuid, cantidad int, precio_visto_bs numeric)
  join public.productos pr on pr.id = x.producto_id;

  select count(*) filter (where problema is not null), sum(subtotal_bs)
    into v_problemas, v_total
  from public.pedido_items
  where pedido_id = p_id;

  if v_problemas = 0 then
    -- 4. Todo válido: confirmado y se descuenta el stock.
    update public.pedido_items
       set precio_aplicado_bs = precio_visto_bs
     where pedido_id = p_id;

    update public.productos pr
       set stock = pr.stock - i.cantidad
      from public.pedido_items i
     where i.pedido_id = p_id
       and pr.id = i.producto_id;

    update public.pedidos
       set estado = 'confirmado', total_bs = v_total
     where id = p_id;
  else
    -- 5. Con problemas: motivo resumen, stock intacto.
    --    Ej.: "sin_stock: P-0004; precio_cambiado: P-0001"
    select string_agg(g.problema || ': ' || g.codigos, '; ' order by g.problema desc)
      into v_motivo
    from (
      select i.problema, string_agg(pr.codigo, ', ' order by pr.codigo) as codigos
      from public.pedido_items i
      join public.productos pr on pr.id = i.producto_id
      where i.pedido_id = p_id and i.problema is not null
      group by i.problema
    ) g;

    update public.pedidos
       set estado = 'con_problema', motivo = v_motivo, total_bs = v_total
     where id = p_id;
  end if;

  -- 6. Estado final y motivos.
  return private.respuesta_pedido(p_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- resolver_pedido
-- p_accion ∈ {aprobar, ajustar, rechazar}
-- p_items_ajustados (solo ajustar) = [{producto_id, cantidad, precio_bs}]
--   - Solo productos que ya están en el pedido; los no listados quedan igual.
--   - cantidad 0 elimina el ítem; precio_bs omitido = precio visto por el vendedor.
-- ---------------------------------------------------------------------------

create or replace function public.resolver_pedido(
  p_id               uuid,
  p_accion           text,
  p_items_ajustados  jsonb default null,
  p_nota             text  default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_estado   text;
  v_n        int;
  v_faltan   text;
  v_nuevo    text;
begin
  if not public.es_supervisor() then
    raise exception 'Solo un supervisor puede resolver pedidos' using errcode = '42501';
  end if;

  if p_accion is null or p_accion not in ('aprobar', 'ajustar', 'rechazar') then
    raise exception 'Acción no válida: debe ser aprobar, ajustar o rechazar' using errcode = '22023';
  end if;

  select estado into v_estado
  from public.pedidos
  where id = p_id
  for update;

  if not found then
    raise exception 'El pedido no existe' using errcode = '22023';
  end if;

  if v_estado <> 'con_problema' then
    raise exception 'Solo se pueden resolver pedidos con problema (estado actual: %)', v_estado
      using errcode = '22023';
  end if;

  if p_accion = 'rechazar' then
    v_nuevo := 'rechazado';

  else
    if p_accion = 'aprobar' then
      -- Se respeta el precio que vio el vendedor.
      update public.pedido_items
         set precio_aplicado_bs = precio_visto_bs,
             subtotal_bs        = round(cantidad * precio_visto_bs, 2)
       where pedido_id = p_id;

    else  -- ajustar
      if p_items_ajustados is null or jsonb_typeof(p_items_ajustados) <> 'array' then
        raise exception 'Para ajustar hay que enviar la lista de ítems ajustados' using errcode = '22023';
      end if;

      if exists (
        select 1
        from jsonb_to_recordset(p_items_ajustados) as a(producto_id uuid, cantidad int, precio_bs numeric)
        where a.producto_id is null
           or a.cantidad is null or a.cantidad < 0
           or (a.precio_bs is not null and (a.precio_bs <= 0 or a.precio_bs <> round(a.precio_bs, 2)))
      ) then
        raise exception 'Ajuste inválido (cantidad >= 0, precio > 0 con 2 decimales)' using errcode = '22023';
      end if;

      select count(*) - count(distinct a.producto_id) into v_n
      from jsonb_to_recordset(p_items_ajustados) as a(producto_id uuid);
      if v_n > 0 then
        raise exception 'Un producto aparece más de una vez en el ajuste' using errcode = '22023';
      end if;

      if exists (
        select 1
        from jsonb_to_recordset(p_items_ajustados) as a(producto_id uuid)
        where not exists (
          select 1 from public.pedido_items i
          where i.pedido_id = p_id and i.producto_id = a.producto_id
        )
      ) then
        raise exception 'Solo se pueden ajustar productos que ya están en el pedido' using errcode = '22023';
      end if;

      delete from public.pedido_items i
      using jsonb_to_recordset(p_items_ajustados) as a(producto_id uuid, cantidad int)
      where i.pedido_id = p_id and i.producto_id = a.producto_id and a.cantidad = 0;

      update public.pedido_items i
         set cantidad           = a.cantidad,
             precio_aplicado_bs = coalesce(a.precio_bs, i.precio_visto_bs)
        from jsonb_to_recordset(p_items_ajustados) as a(producto_id uuid, cantidad int, precio_bs numeric)
       where i.pedido_id = p_id and i.producto_id = a.producto_id and a.cantidad > 0;

      update public.pedido_items
         set precio_aplicado_bs = coalesce(precio_aplicado_bs, precio_visto_bs)
       where pedido_id = p_id;

      update public.pedido_items
         set subtotal_bs = round(cantidad * precio_aplicado_bs, 2)
       where pedido_id = p_id;

      if not exists (select 1 from public.pedido_items where pedido_id = p_id) then
        raise exception 'El ajuste deja el pedido sin productos; usa Rechazar' using errcode = '22023';
      end if;
    end if;

    -- aprobar / ajustar: bloquear productos en orden, exigir stock y descontar.
    perform 1
    from public.productos
    where id in (select producto_id from public.pedido_items where pedido_id = p_id)
    order by id
    for update;

    select string_agg(format('%s (pide %s, hay %s)', pr.codigo, i.cantidad, pr.stock), ', '
                      order by pr.codigo)
      into v_faltan
    from public.pedido_items i
    join public.productos pr on pr.id = i.producto_id
    where i.pedido_id = p_id and pr.stock < i.cantidad;

    if v_faltan is not null then
      raise exception 'Stock insuficiente: %', v_faltan using errcode = 'P0001';
    end if;

    update public.productos pr
       set stock = pr.stock - i.cantidad
      from public.pedido_items i
     where i.pedido_id = p_id
       and pr.id = i.producto_id;

    update public.pedidos
       set total_bs = (select sum(subtotal_bs) from public.pedido_items where pedido_id = p_id)
     where id = p_id;

    v_nuevo := 'confirmado';
  end if;

  update public.pedido_items
     set problema = null
   where pedido_id = p_id;

  update public.pedidos
     set estado          = v_nuevo,
         resuelto_por    = auth.uid(),
         resuelto_en     = now(),
         nota_supervisor = p_nota
   where id = p_id;

  return private.respuesta_pedido(p_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- Permisos: solo usuarios autenticados; cada función valida el rol adentro.
-- ---------------------------------------------------------------------------

revoke execute on function public.enviar_pedido(uuid, uuid, jsonb, timestamptz)   from public, anon;
revoke execute on function public.resolver_pedido(uuid, text, jsonb, text)        from public, anon;

grant execute on function public.enviar_pedido(uuid, uuid, jsonb, timestamptz)    to authenticated;
grant execute on function public.resolver_pedido(uuid, text, jsonb, text)         to authenticated;
