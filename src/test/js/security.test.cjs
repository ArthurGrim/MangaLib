const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { JSDOM, VirtualConsole } = require('jsdom');

const root = path.resolve(__dirname, '../../main/resources');
const source = name => fs.readFileSync(path.join(root, 'static/js', name), 'utf8');

async function setup(t, { template, scripts = [], token = true, status = 200, response = {} } = {}) {
  const html = template ? fs.readFileSync(path.join(root, 'templates', template), 'utf8') : '<head></head><body></body>';
  const resources = [];
  // Layout-Ressourcen nicht laden: getestet wird der reale Formular-DOM, nicht CSS-Rendering.
  const dom = new JSDOM(html.replace(/<link\b[^>]*>/g, ''), {
    url: 'http://localhost:8080/home', runScripts: 'outside-only', virtualConsole: new VirtualConsole(),
    resources: { dispatcher: { dispatch(options) {
      resources.push(options.origin);
      throw new Error('Netzwerk in DOM-Tests gesperrt.');
    } } }
  });
  const w = dom.window;
  // Erst den automatischen DOMContentLoaded abwarten, dann genau einmal unsere Listener auslösen.
  await new Promise(resolve => w.document.addEventListener('DOMContentLoaded', resolve, { once: true }));
  const calls = [], alerts = [];
  w.Headers = Headers;
  w.alert = message => alerts.push(message);
  w.confirm = () => true;
  w.console = { log() {}, error() {} };
  w.XMLHttpRequest = w.WebSocket = class { constructor() { throw new Error('Netzwerk gesperrt.'); } };
  w.fetch = async (url, options) => {
    calls.push({ url, options });
    return { status, ok: status >= 200 && status < 300, json: async () => response };
  };
  w.setTimeout = () => 0;
  for (const [name, value] of [['_csrf', 'test-masked-token'], ['_csrf_header', 'X-CSRF-TOKEN']]) {
    let meta = w.document.querySelector(`meta[name="${name}"]`);
    if (!meta) { meta = w.document.createElement('meta'); meta.name = name; w.document.head.append(meta); }
    meta.content = token ? value : '';
  }
  vm.runInContext(source('security.js'), dom.getInternalVMContext());
  for (const file of scripts) vm.runInContext(source(file), dom.getInternalVMContext(), { filename: file });
  if (scripts.length) w.document.dispatchEvent(new w.Event('DOMContentLoaded'));
  t.after(() => { w.close(); assert.deepEqual(resources, []); });
  return { w, document: w.document, calls, alerts };
}

const settle = () => new Promise(resolve => setImmediate(resolve));

for (const [method, route] of [
  ['POST', '/addMangaReihe'], ['POST', '/editBand'], ['POST', '/addToEinkaufsliste'],
  ['POST', '/buyItemUpdate'], ['POST', '/buyItemCreate'], ['DELETE', '/items/42'],
  ['POST', '/scrape'], ['POST', '/autofillBandData/123']
]) {
  test(`Gemeinsame Fetch-Hilfe erhält ${method} ${route} und den Body mit CSRF`, async t => {
    const e = await setup(t);
    const body = '{"titel":"Manga & Freunde","preis":"6.99"}';
    await e.w.mangaLibFetch(route, { method, headers: { 'Content-Type': 'application/json' }, body });
    assert.equal(e.calls.length, 1);
    assert.equal(e.calls[0].url, 'http://localhost:8080' + route);
    assert.equal(e.calls[0].options.body, body);
    assert.equal(e.calls[0].options.headers.get('X-CSRF-TOKEN'), 'test-masked-token');
    assert.equal(e.calls[0].options.headers.get('Content-Type'), 'application/json');
    assert.equal(e.calls[0].options.credentials, 'same-origin');
    assert.equal(e.calls[0].options.mode, 'same-origin');
    assert.equal(e.calls[0].options.redirect, 'error');
  });
}

for (const url of ['https://evil.invalid/write', '//evil.invalid/write', 'http://localhost:8081/write',
  'http://127.0.0.1:8080/write', 'javascript:alert(1)', 'http://user@localhost:8080/write']) {
  test(`Keine Tokenweitergabe an unzulässiges Ziel ${url}`, async t => {
    const e = await setup(t);
    await assert.rejects(e.w.mangaLibFetch(url, { method: 'POST' }));
    assert.equal(e.calls.length, 0);
    assert.equal(e.alerts.length, 1);
  });
}

test('Leserequest erhält keinen CSRF-Header', async t => {
  const e = await setup(t);
  await e.w.mangaLibFetch('/getBand/42');
  assert.equal(e.calls[0].options.headers.has('X-CSRF-TOKEN'), false);
});

test('Fehlende Tokens verhindern Requests und erklären manuelles Neuladen', async t => {
  const e = await setup(t, { token: false });
  await assert.rejects(e.w.mangaLibFetch('/buyItemUpdate', { method: 'POST' }));
  assert.equal(e.calls.length, 0);
  assert.match(e.alerts[0], /Seite neu laden/);
});

test('403 führt zu verständlicher Meldung ohne automatisches Kauf-Replay', async t => {
  const e = await setup(t, { status: 403 });
  await assert.rejects(e.w.mangaLibFetch('/buyItemUpdate', { method: 'POST' }));
  assert.equal(e.calls.length, 1);
  assert.match(e.alerts[0], /Seite neu laden/);
});

test('Netzwerkfehler wird ohne Retry weitergereicht', async t => {
  const e = await setup(t);
  let count = 0;
  e.w.fetch = async () => { count++; throw new Error('offline'); };
  await assert.rejects(e.w.mangaLibFetch('/buyItemUpdate', { method: 'POST' }));
  assert.equal(count, 1);
});

test('Ein fremdes base-Element kann das geprüfte Fetch-Ziel nicht umleiten', async t => {
  const e = await setup(t);
  const base = e.document.createElement('base');
  base.href = 'https://evil.invalid/';
  e.document.head.append(base);
  await e.w.mangaLibFetch('/editBand', { method: 'POST' });
  assert.equal(e.calls[0].url, 'http://localhost:8080/editBand');
});

function fillForm(e, suffix = '') {
  for (const input of e.document.querySelectorAll('input[type="text"], input[type="number"]')) input.value = '';
  for (const id of ['status', 'verlag', 'typ', 'format']) {
    const select = e.document.getElementById(id + suffix);
    if (select) { select.append(new e.w.Option('Test', '1')); select.value = '1'; }
  }
  for (const [id, value] of [['manga_index', '123'], ['titel', 'Manga & Freunde'],
    ['anzahl_baende', '2'], ['preis_pro_band', '5,99'], ['gesamtpreis_aenderung', '0']]) {
    const input = e.document.getElementById(id + suffix);
    if (input) input.value = value;
  }
  for (const [selector, value] of [['.tag-select', '15'], ['.monat-select', '10'], ['.jahr-select', '2026']]) {
    const select = e.document.querySelector(selector);
    if (select) { select.append(new e.w.Option(value, value)); select.value = value; }
  }
}

for (const [template, script, suffix, button, route] of [
  ['home.html', 'popup_add_reihe.js', '', '.save-button', '/addMangaReihe'],
  ['home.html', 'popup_edit_reihe.js', '-edit', '.save-button-edit', '/addMangaReihe'],
  ['einkaufsliste.html', 'einkaufsliste.js', '', '.save-button', '/addToEinkaufsliste']
]) {
  test(`Echter Speichern-Listener aus ${script} erhält Body und CSRF`, async t => {
    const e = await setup(t, { template, scripts: [script] });
    fillForm(e, suffix);
    e.document.querySelector(button).click();
    await settle();
    assert.equal(e.calls.length, 1, JSON.stringify(e.alerts));
    assert.equal(e.calls[0].url, 'http://localhost:8080' + route);
    assert.equal(e.calls[0].options.method, 'POST');
    assert.equal(e.calls[0].options.headers.get('X-CSRF-TOKEN'), 'test-masked-token');
    const payload = JSON.parse(e.calls[0].options.body);
    assert.equal(payload.titel, 'Manga & Freunde');
    assert.equal(payload.anzahlBaende, 2);
  });
}

for (const [template, script, suffix, button] of [
  ['home.html', 'popup_add_reihe.js', '', '.mp-autofill-button'],
  ['home.html', 'popup_edit_reihe.js', '-edit', '.mp-autofill-button-edit'],
  ['einkaufsliste.html', 'einkaufsliste.js', '', '.mp-autofill-button']
]) {
  test(`Echter Reihen-Autofill-Listener aus ${script} übermittelt CSRF`, async t => {
    const e = await setup(t, { template, scripts: [script], status: 403 });
    e.document.getElementById('manga_index' + suffix).value = '123';
    e.document.querySelector(button).click();
    await settle();
    assert.equal(e.calls.length, 1);
    assert.equal(e.calls[0].url, 'http://localhost:8080/scrape');
    assert.equal(e.calls[0].options.headers.get('X-CSRF-TOKEN'), 'test-masked-token');
    assert.deepEqual(JSON.parse(e.calls[0].options.body), { mangaIndex: '123' });
    assert.match(e.alerts[0], /Seite neu laden/);
  });
}

for (const [button, method, route] of [['.delete-button', 'DELETE', '/items/42'],
  ['.buy-button', 'POST', '/buyItemUpdate']]) {
  test(`Echter Einkaufslisten-Listener ${button} trägt CSRF und wiederholt 403 nicht`, async t => {
    const e = await setup(t, { template: 'einkaufsliste.html', scripts: ['einkaufsliste.js'], status: 403 });
    const node = e.document.querySelector(button);
    node.setAttribute('data-id', '42');
    node.click();
    await settle();
    assert.equal(e.calls.length, 1);
    assert.equal(e.calls[0].url, 'http://localhost:8080' + route);
    assert.equal(e.calls[0].options.method, method);
    assert.equal(e.calls[0].options.headers.get('X-CSRF-TOKEN'), 'test-masked-token');
    assert.match(e.alerts[0], /Seite neu laden/);
  });
}

test('Echter Band-Speichercode erhält Centbeträge und boolesche Felder mit CSRF', async t => {
  const e = await setup(t, { template: 'home.html', scripts: ['popup_edit_baende.js'], status: 204 });
  e.document.getElementById('manga_reihe_id').textContent = '42';
  e.document.getElementById('band_nr').textContent = '1';
  e.document.getElementById('preis').value = '6,99';
  e.document.getElementById('aenderung_preis').value = '-0,02';
  e.document.getElementById('ist_gelesen').checked = true;
  e.w.saveBand();
  await settle();
  assert.equal(e.calls.length, 1);
  assert.equal(e.calls[0].url, 'http://localhost:8080/editBand');
  assert.equal(e.calls[0].options.headers.get('X-CSRF-TOKEN'), 'test-masked-token');
  const payload = JSON.parse(e.calls[0].options.body);
  assert.equal(payload.id, '42');
  assert.equal(payload.preis, '6.99');
  assert.equal(payload.aenderungPreis, '-0.02');
  assert.equal(payload.istGelesen, true);
});

test('Kauf mit Statusauswahl sendet beide bestehenden Payloads jeweils mit CSRF', async t => {
  const e = await setup(t, { template: 'einkaufsliste.html', scripts: ['einkaufsliste.js'],
    status: 417, response: { message: 'Bitte Status für neue Reihe auswählen' } });
  const buy = e.document.querySelector('.buy-button');
  buy.setAttribute('data-id', '42');
  buy.click();
  await settle();
  assert.equal(e.calls.length, 1);
  assert.equal(e.document.querySelector('.ask-for-status-popup').style.visibility, 'visible');
  const select = e.document.getElementById('status');
  select.append(new e.w.Option('Teststatus', '1'));
  select.value = '1';
  e.document.getElementById('confirmStatus').click();
  await settle();
  assert.equal(e.calls.length, 2);
  assert.equal(e.calls[1].url, 'http://localhost:8080/buyItemCreate');
  assert.deepEqual(JSON.parse(e.calls[0].options.body), { itemId: '42' });
  assert.deepEqual(JSON.parse(e.calls[1].options.body), { itemId: '42', statusId: '1' });
  for (const call of e.calls) assert.equal(call.options.headers.get('X-CSRF-TOKEN'), 'test-masked-token');
  assert.equal(e.document.querySelector('.ask-for-status-popup').style.visibility, 'hidden');
});

test('Autofill führt den echten Dialogcode mit POST aus und übernimmt die bisherigen Felder', async t => {
  const e = await setup(t, { template: 'home.html', scripts: ['popup_edit_baende.js'],
    response: { BildUrl: 'https://example.invalid/cover.jpg', Preis: '6,99', mpUrl: 'https://www.manga-passion.de/volumes/123' } });
  e.document.getElementById('band_index').value = '123';
  e.w.autofillBandData();
  await settle();
  assert.equal(e.calls.length, 1);
  assert.equal(e.calls[0].url, 'http://localhost:8080/autofillBandData/123');
  assert.equal(e.calls[0].options.method, 'POST');
  assert.equal(e.calls[0].options.headers.get('X-CSRF-TOKEN'), 'test-masked-token');
  assert.equal(e.document.getElementById('bild_url').value, 'https://example.invalid/cover.jpg');
  assert.equal(e.document.getElementById('editBandCoverImage').getAttribute('src'), 'https://example.invalid/cover.jpg');
  assert.equal(e.document.getElementById('preis').value, '6,99');
  assert.equal(e.document.getElementById('mp_url').value, 'https://www.manga-passion.de/volumes/123');
  assert.equal(e.document.querySelector('.loading-label').style.display, 'none');
});

test('Autofill-403 verändert keine Eingaben und beendet den Ladezustand ohne Wiederholung', async t => {
  const e = await setup(t, { template: 'home.html', scripts: ['popup_edit_baende.js'], status: 403 });
  e.document.getElementById('band_index').value = '123';
  e.document.getElementById('preis').value = '5,99';
  e.w.autofillBandData();
  await settle();
  assert.equal(e.calls.length, 1);
  assert.equal(e.document.getElementById('preis').value, '5,99');
  assert.equal(e.document.querySelector('.loading-label').style.display, 'none');
  assert.match(e.alerts[0], /Seite neu laden/);
});

// Ergänzende Inventur, keine Ersatzprüfung für die oben ausgeführten DOM-/Fetch-Fälle.
test('Alle vorhandenen schreibenden Fetch-Aufrufe verwenden die gemeinsame Hilfe', () => {
  for (const file of ['popup_add_reihe.js', 'popup_edit_reihe.js', 'popup_edit_baende.js', 'einkaufsliste.js']) {
    assert.doesNotMatch(source(file), /(?<![\w])fetch\([^;]*?method:\s*["'](?:POST|DELETE|PUT|PATCH)/s);
    assert.match(source(file), /mangaLibFetch\(/);
  }
});
