/* Linda service worker: keeps the app working with no internet (cache first, fall back to network). */
var CACHE = "linda-v13";
var SHELL = ["./", "./index.html", "./dashboard.html", "./manifest.json", "./capacitor.js", "./icons/icon-192.png", "./icons/icon-512.png", "./icons/icon-maskable-512.png"];
self.addEventListener("install", function (e) { e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(SHELL); }).then(function () { return self.skipWaiting(); })); });
self.addEventListener("activate", function (e) {
  e.waitUntil(caches.keys().then(function (ks) { return Promise.all(ks.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); })); }).then(function () { return self.clients.claim(); }));
});
self.addEventListener("fetch", function (e) {
  if (e.request.method !== "GET") return;
  /* Only our own files are cached. Server data (fraud list, warnings) must always be fresh. */
  if (new URL(e.request.url).origin !== self.location.origin) return;
  /* Server settings: always try the network first so edits show up, fall back to the saved copy offline. */
  if (/linda-config\.js$/.test(new URL(e.request.url).pathname)) {
    e.respondWith(fetch(e.request).then(function (res) { var copy = res.clone(); caches.open(CACHE).then(function (c) { c.put(e.request, copy); }); return res; }).catch(function () { return caches.match(e.request); }));
    return;
  }
  e.respondWith(caches.match(e.request).then(function (hit) {
    var net = fetch(e.request).then(function (res) {
      if (res && (res.ok || res.type === "opaque")) { var copy = res.clone(); caches.open(CACHE).then(function (c) { c.put(e.request, copy); }); }
      return res;
    }).catch(function () { return hit; });
    return hit || net;
  }));
});
