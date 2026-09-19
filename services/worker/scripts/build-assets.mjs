/**
 * Monta a pasta ./public que o Worker publica na borda.
 *
 * Copia o app (apps/pwa) e o motor compilado (packages/core/dist) para
 * ./public/core. Nao e bundler: o navegador carrega os modulos ES direto, que
 * e o mesmo arquivo que os testes deste repositorio exercitam.
 */
import { cp, mkdir, rm } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../../..');
const out = resolve(here, '../public');

await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });
await cp(resolve(repo, 'apps/pwa'), out, { recursive: true });
await cp(resolve(repo, 'packages/core/dist'), resolve(out, 'core'), { recursive: true });

console.log(`assets prontos em ${out}`);
