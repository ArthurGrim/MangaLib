const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { JSDOM, VirtualConsole } = require('jsdom');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/reading-history.js'), 'utf8');
const security = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/security.js'), 'utf8');
const settle = async () => { for (let i=0;i<8;i++) await new Promise(resolve => setImmediate(resolve)); };

async function setup(t, config = {}) {
  const errors = [], reloads = [];
  const console = new VirtualConsole();
  console.on('jsdomError', error => {
    if (error.type === 'not-implemented' && /navigation/.test(error.message)) reloads.push(error);
    else errors.push(error);
  });
  const dom = new JSDOM(`<meta name="_csrf" content="test-token"><meta name="_csrf_header" content="X-CSRF-TOKEN">
    <input id="reread_band"><input id="reread-edit"><input id="apply-reread-edit" type="checkbox">
    <section id="band-reading-history" hidden></section><section id="reading-history-months" hidden></section>`,
    { url: 'http://localhost:18080/home', runScripts: 'outside-only', virtualConsole: console });
  t.after(() => { dom.window.close(); assert.deepEqual(errors, []); });
  dom.window.Headers = Headers;
  const calls = [];
  dom.window.fetch = async (url, options) => {
    calls.push({url, options});
    const data = url.endsWith('/status') ? {enabled: config.enabled !== false, today: '2026-11-15'}
      : url.endsWith('/months') ? {months: [{month: '2026-11', firstReads: 1, rereads: 2, minutes: 135, unknownFactors: 0}]}
      : options.method === 'POST' ? {events:[]} : {events: config.events || []};
    const status = options.method === 'POST' ? config.writeStatus || 200 : 200;
    return { ok: status === 200, status, headers: new Headers({'Content-Type':'application/json'}), json: async () => data };
  };
  vm.runInContext(security, dom.getInternalVMContext());
  vm.runInContext(source, dom.getInternalVMContext()); await settle();
  return { w: dom.window, d: dom.window.document, calls, reloads };
}

test('Nicht migrierte Umgebung fragt keine Historie ab und lässt alte Editoren unverändert', async t => {
  const e = await setup(t, {enabled:false});
  await e.w.mangaLibLoadReadingHistory('7');
  assert.equal(e.d.getElementById('band-reading-history').hidden, true);
  assert.equal(e.d.getElementById('reread_band').readOnly, false);
  assert.deepEqual(e.calls.map(c => new URL(c.url).pathname), ['/reading-history/status']);
});

test('Reread ist eine ausdrückliche CSRF-geschützte Aktion, Metadaten werden nicht gespeichert', async t => {
  const e = await setup(t);
  await e.w.mangaLibLoadReadingHistory('7');
  const section = e.d.getElementById('band-reading-history');
  assert.equal(section.hidden, false); assert.equal(e.d.getElementById('reread_band').readOnly, true);
  const button = [...section.querySelectorAll('button')].find(b => b.textContent === 'Erneut gelesen');
  button.click(); button.click(); await settle();
  const writes = e.calls.filter(c => c.options.method === 'POST');
  assert.equal(writes.length, 1);
  const data = JSON.parse(writes[0].options.body);
  assert.deepEqual(data.bandIds, ['7']); assert.equal(data.kind, 'REREAD'); assert.equal(data.date, '2026-11-15');
  assert.match(data.operationId, /^[0-9a-f-]{36}$/);
  assert.equal(writes[0].options.headers.get('X-CSRF-TOKEN'), 'test-token');
  assert.equal(e.calls.some(c => c.url.endsWith('/editBand')), false);
  assert.equal(e.reloads.length, 1);
});

test('Datumskorrektur sendet nur Eventidentität, Revision und Datum', async t => {
  const e = await setup(t, { events:[{id:4, kind:'FIRST_READ', date:'2026-11-14', revision:2}] });
  await e.w.mangaLibLoadReadingHistory('7');
  const section = e.d.getElementById('band-reading-history');
  const button = [...section.querySelectorAll('button')].find(b => b.textContent === 'Datum korrigieren');
  section.querySelector('li input').value = '2026-11-13'; button.click(); await settle();
  const write = e.calls.find(c => c.options.method === 'POST');
  assert.ok(write.url.endsWith('/bands/7/events/4/date'));
  assert.deepEqual(JSON.parse(write.options.body), {revision:2, date:'2026-11-13'});
  assert.equal([...section.querySelectorAll('button')].some(b => /Löschen|Stornieren/.test(b.textContent)), false);
});

test('Ungültige History-Payload wird als Textfehler behandelt und hat keine Schreibaktionen', async t => {
  const e = await setup(t, {events:[{id:4, kind:'<img src=x onerror=alert(1)>', date:'2026-11-15', revision:0}]});
  await e.w.mangaLibLoadReadingHistory('7');
  const section = e.d.getElementById('band-reading-history');
  assert.match(section.textContent, /nicht geladen/); assert.equal(section.querySelector('img'), null);
  assert.equal(section.querySelector('button'), null);
  assert.equal(e.calls.filter(c => c.options.method === 'POST').length, 0);
});

test('Monatsansicht trennt Lektüren und schreibt niemals', async t => {
  const e = await setup(t);
  const section = e.d.getElementById('reading-history-months');
  assert.equal(section.hidden, false); assert.match(section.textContent, /Erstlektüren/);
  assert.deepEqual([...section.querySelectorAll('td')].map(td => td.textContent), ['2026-11','1','2','135','0']);
  assert.equal(e.calls.filter(c => c.options.method === 'POST').length, 0);
});

test('Monatsansicht verwendet ausdrücklich die vorhandenen dunklen Designfarben', () => {
  const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/reading-history.css'), 'utf8');
  const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/statistik/st_overview.html'), 'utf8');
  const dom = new JSDOM(template);
  const section = dom.window.document.getElementById('reading-history-months');
  assert.ok(section.classList.contains('reading-history'));
  assert.ok(section.closest('.statistik-container'), 'Historie und Diagramme nutzen denselben Inhaltsbereich');
  assert.ok(dom.window.document.querySelector('link[href="/css/reading-history.css"]'));
  // Die Statistik setzt keine globale Schriftfarbe: die neue Sektion muss sie selbst setzen.
  const rule = css.match(/\.reading-history\s*\{([^}]+)\}/)[1];
  assert.match(rule, /(?:^|;)\s*color:\s*var\(--font-page\)/);
  assert.match(rule, /(?:^|;)\s*background:\s*var\(--bg-container\)/);
  dom.window.close();
});

test('Unbestätigtes Schreiben bleibt auch nach erneutem Dialogöffnen gesperrt', async t => {
  const e = await setup(t, {writeStatus:500});
  await e.w.mangaLibLoadReadingHistory('7');
  [...e.d.querySelectorAll('#band-reading-history button')].find(b => b.textContent === 'Erneut gelesen').click();
  await settle(); await e.w.mangaLibLoadReadingHistory('7');
  const section = e.d.getElementById('band-reading-history');
  assert.match(section.textContent, /noch nicht eindeutig bestätigt/);
  assert.equal(section.querySelector('button'), null);
  assert.equal(e.calls.filter(c => c.options.method === 'POST').length, 1);
  assert.equal(e.reloads.length, 0);
});

test('Historienkonflikt zeigt einen festen Fachtext und wiederholt die Aktion nicht', async t => {
  const e = await setup(t, {writeStatus:409});
  await e.w.mangaLibLoadReadingHistory('7');
  const button = [...e.d.querySelectorAll('#band-reading-history button')].find(b => b.textContent === 'Erneut gelesen');
  button.click(); await settle(); button.click(); await settle();
  assert.match(e.d.getElementById('band-reading-history').textContent, /Datumsreihenfolge/);
  assert.equal(e.calls.filter(c => c.options.method === 'POST').length, 1);
  assert.equal(e.reloads.length, 0);
});

test('CSRF-Ablehnung bleibt Sicherheitstext und wird nicht als Fachkonflikt umgedeutet', async t => {
  const e = await setup(t, {writeStatus:403});
  await e.w.mangaLibLoadReadingHistory('7');
  [...e.d.querySelectorAll('#band-reading-history button')].find(b => b.textContent === 'Erneut gelesen').click();
  await settle();
  assert.match(e.d.getElementById('band-reading-history').textContent, /Sicherheitsgründen/);
  assert.doesNotMatch(e.d.getElementById('band-reading-history').textContent, /Datumsreihenfolge/);
  assert.equal(e.calls.filter(c => c.options.method === 'POST').length, 1);
  assert.equal(e.reloads.length, 0);
});
