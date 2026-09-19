import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createApp } from './app.js';
import { openDb } from './db.js';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../../..');

const port = Number(process.env.PORT ?? 8787);
const dbPath = process.env.VAGAS_DB ?? 'vagas.sqlite';

const { server } = createApp({
  db: openDb(dbPath),
  writesPerMinute: Number(process.env.VAGAS_WRITE_LIMIT ?? 60),
  staticMounts: [
    // O motor compilado e servido como ESM: o app do celular roda exatamente
    // o mesmo codigo que os testes deste repositorio.
    { prefix: '/core', dir: resolve(repo, 'packages/core/dist') },
    { prefix: '', dir: resolve(repo, 'apps/pwa') },
  ],
});

server.listen(port, () => {
  console.log(`\n  Vagas rodando em http://localhost:${port}\n`);
  console.log('  Para testar no celular, exponha esta porta com HTTPS:');
  console.log(`    cloudflared tunnel --url http://localhost:${port}\n`);
  console.log('  API:  GET /v1/spots · POST /v1/events · POST /v1/feedback · GET /v1/stats\n');
});
