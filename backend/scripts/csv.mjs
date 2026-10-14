// Lectura de los CSV de backend/datos_semilla/.
// Formato simple: separador coma, primera fila = encabezados, sin comillas.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const DIR = new URL('../datos_semilla/', import.meta.url);

export function leerCsv(nombre, columnas) {
  const ruta = fileURLToPath(new URL(nombre, DIR));
  const lineas = readFileSync(ruta, 'utf8')
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .filter((l) => l.trim() !== '');

  const encabezados = lineas[0].split(',').map((h) => h.trim());
  const faltan = columnas.filter((c) => !encabezados.includes(c));
  if (faltan.length) {
    throw new Error(`${nombre}: faltan columnas ${faltan.join(', ')}`);
  }

  return lineas.slice(1).map((linea, i) => {
    const valores = linea.split(',').map((v) => v.trim());
    if (valores.length !== encabezados.length) {
      throw new Error(`${nombre}, fila ${i + 2}: se esperaban ${encabezados.length} columnas`);
    }
    return Object.fromEntries(encabezados.map((h, j) => [h, valores[j]]));
  });
}
