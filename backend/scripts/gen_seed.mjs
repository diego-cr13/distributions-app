// Genera backend/seed.sql (upsert de productos y clientes) a partir de los CSV.
// Uso: node gen_seed.mjs
// Repetible: upsert por codigo; solo toca filas que cambiaron.

import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { leerCsv } from './csv.mjs';

const SALIDA = fileURLToPath(new URL('../seed.sql', import.meta.url));

const texto = (v) => (v === '' || v == null ? 'null' : `'${v.replaceAll("'", "''")}'`);

const productos = leerCsv('productos.csv', ['codigo', 'nombre', 'unidad', 'precio_bs', 'stock']);
const clientes = leerCsv('clientes.csv', ['codigo', 'nombre', 'zona', 'ciudad']);

for (const p of productos) {
  // precio_bs se importa tal cual: exige formato Bs con 2 decimales, nunca float.
  if (!/^\d+\.\d{2}$/.test(p.precio_bs) || Number(p.precio_bs) <= 0) {
    throw new Error(`productos.csv ${p.codigo}: precio_bs inválido "${p.precio_bs}"`);
  }
  if (!/^\d+$/.test(p.stock)) {
    throw new Error(`productos.csv ${p.codigo}: stock inválido "${p.stock}"`);
  }
}

const filasProductos = productos
  .map((p) => `  (${texto(p.codigo)}, ${texto(p.nombre)}, ${texto(p.unidad)}, ${p.precio_bs}, ${p.stock})`)
  .join(',\n');

const filasClientes = clientes
  .map((c) => `  (${texto(c.codigo)}, ${texto(c.nombre)}, ${texto(c.zona)}, ${texto(c.ciudad)})`)
  .join(',\n');

const sql = `-- seed.sql
-- GENERADO por backend/scripts/gen_seed.mjs desde backend/datos_semilla/. No editar a mano.
-- ${productos.length} productos, ${clientes.length} clientes. Upsert por codigo (repetible).

insert into public.productos (codigo, nombre, unidad, precio_bs, stock) values
${filasProductos}
on conflict (codigo) do update
  set nombre = excluded.nombre,
      unidad = excluded.unidad,
      precio_bs = excluded.precio_bs,
      stock = excluded.stock
  where (productos.nombre, productos.unidad, productos.precio_bs, productos.stock)
        is distinct from (excluded.nombre, excluded.unidad, excluded.precio_bs, excluded.stock);

insert into public.clientes (codigo, nombre, zona, ciudad) values
${filasClientes}
on conflict (codigo) do update
  set nombre = excluded.nombre,
      zona = excluded.zona,
      ciudad = excluded.ciudad
  where (clientes.nombre, clientes.zona, clientes.ciudad)
        is distinct from (excluded.nombre, excluded.zona, excluded.ciudad);
`;

writeFileSync(SALIDA, sql, 'utf8');
console.log(`seed.sql generado: ${productos.length} productos, ${clientes.length} clientes → ${SALIDA}`);
