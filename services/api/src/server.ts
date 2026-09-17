import { createApp } from './app.js';
import { openDb } from './db.js';

const port = Number(process.env.PORT ?? 8787);
const dbPath = process.env.VAGAS_DB ?? 'vagas.sqlite';

const { server } = createApp({ db: openDb(dbPath) });

server.listen(port, () => {
  console.log(`[vagas] API ouvindo em http://localhost:${port} (db: ${dbPath})`);
  console.log('        GET  /healthz');
  console.log('        POST /v1/events    { events: [...] }');
  console.log('        GET  /v1/spots?lat=..&lon=..&radius=800');
  console.log('        POST /v1/feedback  { lat, lon, found }');
});
