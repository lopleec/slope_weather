import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

// Run the exact bundled map controller with deterministic Leaflet tile events.
// No network, browser dependency, phone state, or invented radar frames in the app.
const source = process.argv[2] ?? new URL('../app/src/main/assets/map.html', import.meta.url);
const html = fs.readFileSync(source, 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
const controller = scripts.at(-1)[1];
const tiles = [], shown = [], failures = [], tooltips = [];
const boxes = new Map();
const evented = (extra = {}) => ({
  handlers: new Map(),
  on(name, fn) { this.handlers.set(name, fn); return this; },
  emit(name) { this.handlers.get(name)?.(); },
  addTo() { return this; },
  remove() { this.removed = true; return this; },
  ...extra,
});
const map = evented({
  attributionControl: { setPrefix() {} },
  setView() { return this; },
  invalidateSize() {},
});
const bridge = { frameShown(i) { shown.push(i); }, frameFailed() { failures.push(true); } };
const context = {
  window: { SlopeMap: bridge }, SlopeMap: bridge,
  document: {
    getElementById(id) {
      if (!boxes.has(id)) boxes.set(id, { style: {}, textContent: '' });
      return boxes.get(id);
    },
    createTextNode(text) { return { nodeType: 3, textContent: text }; },
  },
  setTimeout(fn) { fn(); },
  L: {
    map() { return map; },
    circleMarker() {
      return evented({ bindTooltip(node) { tooltips.push(node); } });
    },
    tileLayer(url) {
      const layer = evented({
        url, opacity: 0,
        getContainer() { return { style: {} }; },
        setOpacity(value) { this.opacity = value; return this; },
      });
      tiles.push(layer);
      return layer;
    },
  },
};
vm.createContext(context);
vm.runInContext(controller, context);
const config = {
  lat: 48.86, lon: 2.35, name: '<img src=x onerror=alert(1)>',
  style: 'osm', date: '2026-10-22',
  radar: { frames: [{ url: 'frame-a' }, { url: 'frame-b' }], maxZoom: 7, attribution: 'test' },
  error: 'unavailable', baseError: 'base unavailable', loading: 'loading', partial: 'partial',
};
const finish = (layer, successes = 1, errors = 0) => {
  layer.emit('loading');
  for (let i = 0; i < successes; i++) layer.emit('tileload');
  for (let i = 0; i < errors; i++) layer.emit('tileerror');
  layer.emit('load');
};
context.window.configure(config);
assert.equal(tooltips[0].nodeType, 3);
assert.equal(tooltips[0].textContent, config.name);
finish(tiles.find(t => t.url === 'frame-b'));
finish(tiles.find(t => t.url === 'frame-a'));
assert.deepEqual(shown, [1]);

// A sub-tile pan can emit movement without loading any new tiles.
map.emit('movestart');
map.emit('moveend');
context.window.setFrame(0);
assert.deepEqual(shown, [1, 0], 'A cached frame must remain playable after a pan with no tile requests');

// Removed layers can finish late after a source change.
const obsolete = tiles.find(t => t.url === 'frame-b');
context.window.configure({
  ...config, radar: { ...config.radar, frames: [{ url: 'new-a' }, { url: 'new-b' }] },
});
const before = shown.length;
obsolete.emit('load');
assert.equal(shown.length, before, 'An old source cannot acknowledge a new frame');
const latest = tiles.find(t => t.url === 'new-b');
finish(latest, 0, 2);
assert.equal(failures.length, 1);
assert.equal(shown.length, before, 'An entirely missing frame must not be marked shown');
finish(latest, 1, 1);
assert.equal(shown.at(-1), 1);
assert.equal(boxes.get('message').textContent, 'partial');
console.log(JSON.stringify({
  passed: true,
  checks: ['cached playback after no-load pan', 'stale source callback', 'complete tile failure', 'partial coverage', 'place label remains text'],
}));
