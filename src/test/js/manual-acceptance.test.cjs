const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { JSDOM, VirtualConsole } = require('jsdom');
const root = path.resolve(__dirname, '../../main/resources');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const settle = async () => { for (let i = 0; i < 4; i++) await new Promise(r => setImmediate(r)); };

async function edit(t, { flags = {}, result = {}, type = 'application/json', status = 200, network = false } = {}) {
  const resources = [];
  const dom = new JSDOM(read('templates/home.html').replace(/<link\b[^>]*>/g, ''), {
    url: 'http://localhost:18080/home', runScripts: 'outside-only', virtualConsole: new VirtualConsole(),
    resources: { dispatcher: { dispatch(options) { resources.push(options.origin); throw new Error('Netzwerk gesperrt'); } } }
  });
  const w = dom.window, d = w.document, calls = [], alerts = [], parsed = [];
  await new Promise(r => d.addEventListener('DOMContentLoaded', r, { once: true }));
  w.Headers = Headers; w.alert = message => alerts.push(message); w.console = { log() {}, error() {} };
  w.XMLHttpRequest = w.WebSocket = class { constructor() { throw new Error('Netzwerk gesperrt'); } };
  w.setTimeout = () => 0;
  for (const [name, value] of [['_csrf', 'synthetic-mask'], ['_csrf_header', 'X-CSRF-TOKEN']]) d.querySelector(`meta[name="${name}"]`).content = value;
  for (const id of ['status', 'verlag', 'typ', 'format', 'sammelband_typ']) d.getElementById(id + '-edit').append(new w.Option('Test', '1'));
  w.fetch = async (url, options) => {
    calls.push({ url, options });
    if (!options) return { json: async () => ({ id: 2, mangaIndex: 900002, titel: 'Titel', statusId: '1', verlagId: '1', typId: '1', formatId: '1',
      anzahlBaende: 2, preisProBand: 7.49, coverUrl: 'http://localhost:18080/manual-test/cover.svg', istGelesen: true, reread: 1, sammelbandTypId: '1', ...flags }) };
    if (network) throw new Error('private network detail');
    return { ok: status >= 200 && status < 300, status, headers: new Headers({ 'Content-Type': type }), json: async () => {
      parsed.push(true); if (result === 'malformed') throw new SyntaxError('private parse detail'); return result;
    }, text: async () => { throw new Error('Interne Serverantwort darf nicht angezeigt werden'); } };
  };
  for (const file of ['security.js', 'popup_edit_reihe.js']) vm.runInContext(read('static/js/' + file), dom.getInternalVMContext());
  d.dispatchEvent(new w.Event('DOMContentLoaded'));
  d.querySelector('.edit-button').setAttribute('data-id', '2'); d.querySelector('.edit-button').click();
  await settle();
  assert.equal(d.getElementById('editPopupContainer').style.display, 'flex');
  d.getElementById('titel-edit').value = 'Neuer Titel';
  t.after(() => { w.close(); assert.deepEqual(resources, []); });
  const save = async () => { d.querySelector('.save-button-edit').click(); await settle(); };
  return { w, d, calls, alerts, parsed, save, payload: () => JSON.parse(calls[1].options.body) };
}

for (const value of [null, undefined, false, true]) {
  test(`Reine Titeländerung erhält beide nullable Boolean-Werte: ${value}`, async t => {
    const e = await edit(t, { flags: { istVergriffen: value, istEbayPreis: value } }); await e.save();
    assert.equal(e.payload().istVergriffen, value ?? null); assert.equal(e.payload().istEbayPreis, value ?? null);
    assert.equal(e.payload().titel, 'Neuer Titel'); assert.equal(e.payload().istGelesen, true); assert.equal(e.payload().reread, 1);
    assert.equal(e.payload().coverUrl, 'http://localhost:18080/manual-test/cover.svg');
    assert.equal(e.payload().anilistUrl, null);
    assert.equal(e.calls[1].options.headers.get('X-CSRF-TOKEN'), 'synthetic-mask');
    assert.equal(e.alerts.length, 0); assert.equal(e.d.getElementById('editPopupContainer').style.display, 'none');
  });
}
test('Unberührte fehlende Cover-URL bleibt NULL, bewusste URL-Änderungen bleiben möglich', async t => {
  const e = await edit(t, { flags: { coverUrl: null, anilistUrl: null } });
  e.d.getElementById('anilist_url-edit').value = 'http://localhost:18080/home?test=2';
  await e.save(); assert.equal(e.payload().coverUrl, null);
  assert.equal(e.payload().anilistUrl, 'http://localhost:18080/home?test=2');
});
for (const id of ['istvergriffen-edit', 'istebaypreis-edit']) {
  for (const checked of [true, false]) {
    test(`Bewusste Änderung von NULL an ${id} zu ${checked}`, async t => {
      const e = await edit(t); const checkbox = e.d.getElementById(id);
      // false entsteht durch bewusstes Ein- und Zurückschalten, nicht durch Öffnen.
      checkbox.checked = true; checkbox.dispatchEvent(new e.w.Event('change'));
      checkbox.checked = checked; checkbox.dispatchEvent(new e.w.Event('change')); await e.save();
      const field = id.startsWith('istvergriffen') ? 'istVergriffen' : 'istEbayPreis';
      assert.equal(e.payload()[field], checked); assert.equal(e.payload()[field === 'istVergriffen' ? 'istEbayPreis' : 'istVergriffen'], null);
    });
  }
}
test('Neue Dialogöffnung verwirft die NULL-Änderungsmarkierung des vorherigen Dialogs', async t => {
  const e = await edit(t); const checkbox = e.d.getElementById('istvergriffen-edit');
  checkbox.checked = true; checkbox.dispatchEvent(new e.w.Event('change'));
  e.d.querySelector('.cancel-button-edit').click(); e.d.querySelector('.edit-button').click(); await settle();
  await e.save(); assert.equal(JSON.parse(e.calls[2].options.body).istVergriffen, null);
});
for (const config of [
  { name: '400 Textantwort', status: 400, type: 'text/plain', parses: 0 },
  { name: '500 Textantwort', status: 500, type: 'text/plain', parses: 0 },
  { name: '403 Sicherheitsablehnung', status: 403, type: 'text/html', parses: 0, csrf: true },
  { name: '200 HTML-Antwort', type: 'text/html', parses: 0 },
  { name: '200 ungültiges JSON', result: 'malformed', parses: 1 },
  { name: '200 leerer JSON-Wert', result: null, parses: 1 },
  { name: 'Netzwerkfehler', network: true, parses: 0 }
]) {
  test(`Speichern behandelt ${config.name} kontrolliert ohne Replay`, async t => {
    const e = await edit(t, config); await e.save();
    assert.equal(e.calls.length, 2); assert.equal(e.parsed.length, config.parses); assert.equal(e.alerts.length, 0);
    const feedback = e.d.querySelector('#editPopupContainer .write-feedback');
    assert.equal(feedback.hidden, false);
    assert.match(feedback.textContent, config.csrf ? /Seite neu/ : config.status === 400 ? /ungültig/ : config.status === 500 ? /Fehler aufgetreten/ : /nicht eindeutig bestätigt/);
    assert.doesNotMatch(feedback.textContent, /private|NullPointer|SQLException|JSON.parse/);
    assert.equal(e.d.getElementById('editPopupContainer').style.display, 'flex');
    assert.equal(e.d.getElementById('titel-edit').value, 'Neuer Titel');
  });
}

const bannerRoot = path.resolve(__dirname, '../../manualTest/resources/static/manual-test');
for (const page of ['home.html', 'einkaufsliste.html', ...['overview', 'status', 'verlag', 'typ', 'format'].map(n => 'statistik/st_' + n + '.html')]) {
  test(`Manuelle Banner-Abstände und Resize-Verhalten: ${page}`, async t => {
    // th:if wird hier nicht interpretiert; normale/manuelle Auswahl prüft der separate Thymeleaf-Test.
    const html = read('templates/' + page).replace(/<link\b[^>]*>/g, '');
    const dom = new JSDOM(html, { runScripts: 'outside-only', virtualConsole: new VirtualConsole() });
    t.after(() => dom.window.close());
    const w = dom.window, d = w.document;
    const style = d.createElement('style'); style.textContent = fs.readFileSync(path.join(bannerRoot, 'banner.css'), 'utf8'); d.head.append(style);
    const banner = d.querySelector('.manual-test-banner'); assert.equal(banner.getAttribute('role'), 'status');
    assert.match(banner.textContent, /MANUELLE TESTUMGEBUNG/);
    let height = 42, resize;
    banner.getBoundingClientRect = () => ({ height });
    w.ResizeObserver = class { constructor(callback) { resize = callback; } observe(node) { assert.equal(node, banner); } };
    vm.runInContext(fs.readFileSync(path.join(bannerRoot, 'banner.js'), 'utf8'), dom.getInternalVMContext());
    assert.equal(d.documentElement.style.getPropertyValue('--manual-banner-height'), '42px');
    height = 84; resize(); assert.equal(d.documentElement.style.getPropertyValue('--manual-banner-height'), '84px');
    height = 96; w.dispatchEvent(new w.Event('resize')); assert.equal(d.documentElement.style.getPropertyValue('--manual-banner-height'), '96px');
    height = NaN; resize(); assert.equal(d.documentElement.style.getPropertyValue('--manual-banner-height'), '96px');
    const rules = [...style.sheet.cssRules];
    assert.equal(rules.find(r => r.selectorText === '.header, .sidebar').style.getPropertyValue('top'), 'var(--manual-banner-height)');
    assert.equal(rules.find(r => r.selectorText === 'body').style.getPropertyValue('padding-top'), 'var(--manual-banner-height)');
    assert.equal(w.getComputedStyle(banner).position, 'fixed'); assert.equal(w.getComputedStyle(banner).zIndex, '100');
    assert.equal(rules.find(r => r.selectorText.includes('.popup-container')).style.getPropertyValue('height'), 'calc(100% - var(--manual-banner-height))');
  });
}
