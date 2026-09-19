/**
 * Service worker minimo.
 *
 * Existe por dois motivos: deixar o app instalavel na tela inicial e abrir
 * offline. Nao guarda nada alem da casca, e sempre prefere a rede — durante
 * um piloto, uma correcao precisa chegar no proximo refresh, e cache agressivo
 * transformaria isso em suporte por telefone.
 */
const CACHE = 'vagas-shell-v1';
const SHELL = ['/', '/app.js', '/manifest.webmanifest', '/icon-192.png', '/icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  // Chamadas de API nunca saem do cache: dado velho de vaga e pior que nenhum.
  if (url.pathname.startsWith('/v1/')) return;

  e.respondWith(
    fetch(e.request)
      .then((res) => {
        if (res.ok && url.origin === self.location.origin) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(e.request, copy));
        }
        return res;
      })
      .catch(() => caches.match(e.request).then((hit) => hit || caches.match('/'))),
  );
});
