const CACHE_NAME = 'precision-nutrition-v2';
const OFFLINE_URL = '/offline';

const PRECACHE_ASSETS = [
  OFFLINE_URL,
  '/icons/icon-192x192.png',
  '/icons/icon-512x512.png',
  '/icons/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((cacheNames) =>
        Promise.all(
          cacheNames
            .filter((name) => name !== CACHE_NAME)
            .map((oldName) => caches.delete(oldName))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;

  // Regra estrita: NUNCA interceptar POST, PUT, DELETE, PATCH ou outros métodos não-GET
  if (request.method !== 'GET') {
    return;
  }

  // Regra estrita: NUNCA interceptar Next.js Server Actions
  if (request.headers.has('Next-Action') || request.headers.has('next-action')) {
    return;
  }

  const url = new URL(request.url);

  // Ignorar requisições cross-origin
  if (url.origin !== self.location.origin) {
    return;
  }

  // Regra estrita: NUNCA interceptar ou fazer cache de rotas /api/
  if (url.pathname.startsWith('/api/')) {
    return;
  }

  // Navegações de página: Network-First com fallback para /offline
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(async () => {
        const cachedOffline = await caches.match(OFFLINE_URL);
        if (cachedOffline) {
          return cachedOffline;
        }
        return new Response('Sem conexão com o servidor.', {
          status: 503,
          headers: { 'Content-Type': 'text/plain; charset=utf-8' },
        });
      })
    );
    return;
  }

  // Assets estáticos locais (/icons/): Cache-First
  if (url.pathname.startsWith('/icons/')) {
    event.respondWith(
      caches.match(request).then((cachedResponse) => {
        if (cachedResponse) {
          return cachedResponse;
        }
        return fetch(request).then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200 && networkResponse.type === 'basic') {
            const responseClone = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, responseClone));
          }
          return networkResponse;
        });
      })
    );
  }
});
