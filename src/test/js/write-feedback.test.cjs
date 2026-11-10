const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { JSDOM, VirtualConsole } = require('jsdom');
const root = path.resolve(__dirname, '../../main/resources');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const settle = async () => { for (let i = 0; i < 4; i++) await new Promise(r => setImmediate(r)); };

async function setup(t, script = 'popup_add_reihe.js', config = {}) {
  const shopping = script === 'einkaufsliste.js';
  const resources = [], errors = [], calls = [], alerts = [], reloads = [];
  const console = new VirtualConsole(); console.on('jsdomError', e => {
    // jsdom navigiert nicht. Genau den erwarteten Reload erfassen, andere Fehler nicht ausblenden.
    if (e.type === 'not-implemented' && e.message === 'Not implemented: navigation to another Document') reloads.push(e);
    else errors.push(e);
  });
  const dom = new JSDOM(read('templates/' + (shopping ? 'einkaufsliste' : 'home') + '.html').replace(/<link\b[^>]*>/g, ''), {
    url: 'http://localhost:18080/home', runScripts: 'outside-only', virtualConsole: console,
    resources: { dispatcher: { dispatch(options) { resources.push(options.origin); throw new Error('Netzwerk gesperrt'); } } }
  });
  const w = dom.window, d = w.document;
  if (shopping) d.querySelectorAll('.buy-button').forEach(button => { button.dataset.id = '7'; });
  await new Promise(r => d.addEventListener('DOMContentLoaded', r, { once: true }));
  w.Headers = Headers; w.alert = value => alerts.push(value); w.confirm = () => true;
  w.console = { log() {}, error: (...args) => errors.push(args.join(' ')) };
  w.XMLHttpRequest = w.WebSocket = class { constructor() { throw new Error('Netzwerk gesperrt'); } };
  w.setTimeout = () => 0;
  for (const [name, value] of [['_csrf', 'synthetic-token'], ['_csrf_header', 'X-CSRF-TOKEN']]) d.querySelector(`meta[name="${name}"]`).content = value;
  for (const suffix of ['', '-edit']) {
    for (const id of ['status', 'verlag', 'typ', 'format']) {
      const field = d.getElementById(id + suffix);
      if (field) { field.append(new w.Option('Test', '1')); field.value = '1'; }
    }
    for (const [id, value] of [['titel', 'Unverändert & 漫画'], ['anzahl_baende', '2'], ['preis_pro_band', '6,99'], ['reread', '1'], ['gesamtpreis_aenderung', '0']]) {
      const field = d.getElementById(id + suffix); if (field) field.value = value;
    }
  }
  for (const selector of ['.tag-select', '.monat-select', '.jahr-select']) {
    const field = d.querySelector(selector); if (field) { const value = selector === '.jahr-select' ? '2026' : '1'; field.append(new w.Option(value, value)); field.value = value; }
  }
  if (!shopping) {
    d.getElementById('manga_reihe_id').textContent = '3'; d.getElementById('band_nr').textContent = '1';
    d.getElementById('preis').value = '6,99'; d.getElementById('aenderung_preis').value = '0';
  }
  let parsed = 0, textRead = 0;
  w.fetch = async (url, options) => {
    calls.push({ url, options });
    if (config.network) throw new w.DOMException('private transport detail', config.network);
    return { status: config.status ?? 200, ok: (config.status ?? 200) < 300,
      headers: new Headers({ 'Content-Type': config.type ?? 'application/json',
        ...(config.outcome ? { 'X-MangaLib-Purchase-Outcome': config.outcome } : {}) }),
      json: async () => { parsed++; if (config.malformed || config.emptyBody) throw new SyntaxError('private parse detail'); return config.data === undefined ? { id: 3 } : config.data; },
      text: async () => { textRead++; return '<script>private SQLException secret</script>'; }
    };
  };
  for (const file of ['security.js', script]) vm.runInContext(read('static/js/' + file), dom.getInternalVMContext(), { filename: file });
  d.dispatchEvent(new w.Event('DOMContentLoaded'));
  const id = shopping || script === 'popup_add_reihe.js' ? 'popupContainer' : script === 'popup_edit_reihe.js' ? 'editPopupContainer' : 'editBaendePopupContainer';
  const container = d.getElementById(id); container.style.display = 'flex';
  const button = container.querySelector('.save-button, .save-button-edit');
  const save = async () => { if (script === 'popup_edit_baende.js') w.saveBand(); else button.click(); await settle(); };
  t.after(() => { w.close(); assert.deepEqual(resources, []); assert.equal(alerts.length, 0); assert.deepEqual(errors, []); });
  return { w, d, calls, container, button, save, reloads,
    evaluate: file => vm.runInContext(read('static/js/' + file), dom.getInternalVMContext(), { filename: file }),
    parsed: () => parsed, textRead: () => textRead };
}

test('Einzelband lädt nur eine CSRF-geschützte Vorschau und lässt die Reihe unverändert', async t => {
  const e = await setup(t, 'popup_add_reihe.js', { data: {
    Titel: 'Testband & 漫画', Preis: '8,49', BildUrl: 'https://example.invalid/cover.jpg',
    Veröffentlichung: '12.11.2026'
  } });
  const mode = e.d.getElementById('import_mode');
  const count = e.d.getElementById('anzahl_baende');
  const index = e.d.getElementById('manga_index');
  const autofill = e.container.querySelector('.mp-autofill-button');
  assert.equal(mode.value, 'series');
  index.value = '2502';
  mode.value = 'volume';
  mode.dispatchEvent(new e.w.Event('change'));
  assert.equal(count.value, '1');
  assert.equal(count.readOnly, true);
  assert.equal(index.value, '');
  index.value = '15295';
  autofill.click();
  await e.save();
  assert.deepEqual(e.calls.map(call => call.url), ['http://localhost:18080/autofillBandData/15295?importPreview']);
  assert.equal(e.calls[0].options.method, 'POST');
  assert.equal(new Headers(e.calls[0].options.headers).get('X-CSRF-TOKEN'), 'synthetic-token');
  assert.equal(e.d.getElementById('titel').value, 'Testband & 漫画');
  assert.equal(e.d.getElementById('preis_pro_band').value, '8,49');
  assert.equal(e.d.getElementById('volume_import_cover').value, 'https://example.invalid/cover.jpg');
  assert.equal(e.d.getElementById('volume_import_date').value, '12.11.2026');
  mode.value = 'series';
  mode.dispatchEvent(new e.w.Event('change'));
  assert.equal(count.value, '2');
  assert.equal(count.readOnly, false);
  assert.equal(index.value, '');
  assert.equal(autofill.disabled, false);
  assert.equal(e.button.hidden, false);
  await e.save();
  assert.equal(e.d.getElementById('volume_import_cover').value, '');
  assert.equal(JSON.parse(e.calls[1].options.body).importMode, 'series');
});

test('Einzelband sendet bestätigte Zielreihe und Bandnummer getrennt vom Bestand', async t => {
  const e = await setup(t, 'popup_add_reihe.js', { data: {
    Titel: 'Blue Lock, Band 31', Bandnummer: '31', Preis: '8,50'
  } });
  const mode = e.d.getElementById('import_mode');
  mode.value = 'volume';
  mode.dispatchEvent(new e.w.Event('change'));
  e.d.getElementById('manga_index').value = '24092';
  e.container.querySelector('.mp-autofill-button').click();
  await settle();
  const target = e.d.getElementById('volume_import_target');
  target.append(new e.w.Option('Blue Lock (#42)', '42'));
  target.value = '42';
  await e.save();
  assert.equal(e.calls.length, 2);
  const data = JSON.parse(e.calls[1].options.body);
  assert.equal(data.targetSeriesId, '42');
  assert.equal(data.bandIndex, '24092');
  assert.equal(data.bandNumber, '31');
  assert.equal(data.anzahlBaende, 1);
  assert.equal(data.titel, 'Blue Lock');
});

const failures = [
  { name: '413 Größenbegrenzung', status: 413, message: /technischen Größenbegrenzungen.*Seite.*neu/, parsed: 0, blocked: true },
  { name: '415 Anfrageformat', status: 415, message: /Anfrageformat wird nicht unterstützt/, parsed: 0, blocked: false },
  { name: '400 JSON', status: 400, message: /ungültig/, parsed: 0, blocked: false },
  { name: '400 Text', status: 400, type: 'text/plain', message: /ungültig/, parsed: 0, blocked: false },
  { name: '403 HTML', status: 403, type: 'text/html', message: /Sicherheitsgründen.*Seite neu/, parsed: 0, blocked: true },
  { name: '404 leer', status: 404, message: /nicht gefunden/, parsed: 0, blocked: false },
  { name: '417 außerhalb des Kaufvertrags', status: 417, message: /nicht eindeutig bestätigt/, parsed: 0, blocked: true },
  { name: '500 HTML', status: 500, type: 'text/html', message: /Fehler aufgetreten/, parsed: 0, blocked: true },
  { name: '200 HTML', type: 'text/html', message: /nicht eindeutig bestätigt/, parsed: 0, blocked: true },
  { name: '200 defektes JSON', malformed: true, message: /nicht eindeutig bestätigt/, parsed: 1, blocked: true },
  { name: '200 leer', data: null, message: /nicht eindeutig bestätigt/, parsed: 1, blocked: true },
  { name: '200 ohne Body', emptyBody: true, message: /nicht eindeutig bestätigt/, parsed: 1, blocked: true },
  { name: 'Netzwerkfehler', network: 'NetworkError', message: /nicht eindeutig bestätigt/, parsed: 0, blocked: true },
  { name: 'Abbruch/Timeout', network: 'AbortError', message: /nicht eindeutig bestätigt/, parsed: 0, blocked: true }
];
for (const script of ['popup_add_reihe.js', 'popup_edit_reihe.js', 'popup_edit_baende.js', 'einkaufsliste.js']) {
  for (const config of failures) {
    test(`${script}: ${config.name} bleibt sicher und nicht blockierend`, async t => {
      const e = await setup(t, script, config);
      const values = [...e.container.querySelectorAll('input, select')].map(f => [f.id, f.value, f.checked]);
      await e.save();
      assert.equal(e.calls.length, 1); assert.equal(e.parsed(), config.parsed); assert.equal(e.textRead(), 0);
      assert.equal(e.reloads.length, 0);
      assert.equal(e.container.style.display, 'flex');
      assert.deepEqual([...e.container.querySelectorAll('input, select')].map(f => [f.id, f.value, f.checked]), values);
      const label = e.container.querySelector('.write-feedback');
      assert.equal(label.hidden, false); assert.equal(label.getAttribute('role'), 'alert'); assert.equal(label.children.length, 0);
      assert.match(label.textContent, config.message); assert.doesNotMatch(label.textContent, /private|secret|SQLException|script|Session.*abgelaufen/);
      assert.equal(e.d.activeElement, label); assert.equal(e.button.disabled, config.blocked);
      if (config.blocked) { await e.save(); assert.equal(e.calls.length, 1); }
      assert.equal(e.calls[0].options.headers.get('X-CSRF-TOKEN'), 'synthetic-token');
    });
  }
}

for (const script of ['popup_add_reihe.js', 'popup_edit_reihe.js', 'einkaufsliste.js']) {
  test(`${script}: lokaler leerer Titel zeigt Meldung ohne HTTP; Cancel bleibt bedienbar`, async t => {
    const e = await setup(t, script);
    e.d.getElementById(script === 'popup_edit_reihe.js' ? 'titel-edit' : 'titel').value = '   ';
    await e.save(); assert.equal(e.calls.length, 0);
    assert.match(e.container.querySelector('.write-feedback').textContent, /Titel/);
    e.container.querySelector('.cancel-button, .cancel-button-edit').click(); assert.equal(e.container.style.display, 'none');
    if (script === 'popup_add_reihe.js') { e.d.getElementById('addButton').click(); assert.equal(e.container.style.display, 'flex'); assert.equal(e.container.querySelector('.write-feedback').hidden, true); }
  });
}

test('Band-204 ohne Body schließt Dialog; Band-ID-Beschriftung und Cancel bleiben erhalten', async t => {
  const e = await setup(t, 'popup_edit_baende.js', { status: 204 });
  assert.equal(e.d.querySelector('label[for="manga_reihe_id"]').textContent, 'Band-ID');
  assert.equal(e.d.getElementById('manga_reihe_id').getAttribute('name'), 'manga_reihe_id');
  e.w.closeEditBandPopup(); assert.equal(e.container.style.display, 'none'); e.container.style.display = 'flex';
  await e.save(); assert.equal(e.parsed(), 0); assert.equal(e.textRead(), 0); assert.equal(e.container.style.display, 'none');
  assert.equal(e.reloads.length, 1);
  assert.equal(JSON.parse(e.calls[0].options.body).id, '3');
});

test('Gleichzeitiger zweiter Klick löst keinen zweiten Schreibrequest aus', async t => {
  const e = await setup(t);
  let release;
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); return new Promise(r => { release = r; }); };
  e.button.click(); e.button.click(); await settle(); assert.equal(e.calls.length, 1); assert.equal(e.button.disabled, true);
  release({ status: 400, ok: false }); await settle(); assert.equal(e.button.disabled, false);
});

test('Kontrollierter 400 erlaubt bewusste Korrektur; Meldung wird durch neue Aktion entfernt', async t => {
  const e = await setup(t, 'popup_add_reihe.js', { status: 400 });
  await e.save(); assert.equal(e.calls.length, 1); assert.equal(e.button.disabled, false);
  await e.save(); assert.equal(e.calls.length, 2); // ausdrücklicher Klick, kein automatisches Replay
  assert.equal(e.container.querySelectorAll('.write-feedback').length, 1);
});

test('Lokaler manueller Ersatzcover verhindert externen Bildversuch; normale Cover bleiben erhalten', async t => {
  const e = await setup(t);
  const image = e.d.createElement('img'), external = 'https://upload.wikimedia.org/placeholder.png';
  e.w.mangaLibSetCover(image, external); assert.equal(image.src, external);
  image.dispatchEvent(new e.w.Event('error')); assert.equal(image.getAttribute('src'), '/svg/filler_img2.png'); assert.equal(image.onerror, null);
  e.d.body.dataset.coverPlaceholder = '/manual-test/cover.svg';
  e.w.mangaLibSetCover(image, external); assert.equal(image.getAttribute('src'), '/manual-test/cover.svg');
  e.w.mangaLibSetCover(image, 'http://localhost:18080/manual-test/cover.svg'); assert.equal(image.src, 'http://localhost:18080/manual-test/cover.svg');
  e.w.mangaLibSetCover(image, null); assert.equal(image.getAttribute('src'), '/manual-test/cover.svg');
});

test('417 öffnet Statusdialog; 400 beim Create behält Auswahl und erlaubt explizite Korrektur', async t => {
  const e = await setup(t, 'einkaufsliste.js', { status: 417, data: { message: 'Bitte Status für neue Reihe auswählen' } });
  const buy = e.d.querySelector('.buy-button'); buy.click(); await settle();
  const popup = e.d.querySelector('.ask-for-status-popup'); assert.equal(popup.style.visibility, 'visible');
  e.d.getElementById('status').value = '1';
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); return { status: 400, ok: false }; };
  e.d.getElementById('confirmStatus').click(); await settle();
  assert.equal(e.calls.length, 2); assert.equal(popup.style.visibility, 'visible'); assert.equal(e.d.getElementById('status').value, '1');
  assert.match(popup.querySelector('.write-feedback').textContent, /ungültig/);
  assert.equal(e.d.getElementById('confirmStatus').disabled, false);
  e.d.getElementById('confirmStatus').click(); await settle(); assert.equal(e.calls.length, 3);
});

test('417 mit falschem Payload öffnet keinen Kaufdialog und sperrt erneuten Kauf', async t => {
  const e = await setup(t, 'einkaufsliste.js', { status: 417, data: { message: 'private secret' } });
  const buy = e.d.querySelector('.buy-button'); buy.click(); await settle(); buy.click(); await settle();
  assert.equal(e.calls.length, 1); assert.equal(buy.disabled, true);
  assert.notEqual(e.d.querySelector('.ask-for-status-popup').style.visibility, 'visible');
  assert.doesNotMatch(e.d.querySelector('main > .write-feedback').textContent, /private secret/);
});

test('Banddialog öffnet und schließt wiederholt ohne veralteten Close-Listener', async t => {
  const e = await setup(t, 'popup_edit_baende.js');
  const data = { id: 3, bandNr: 2, titel: 'Titel', bandIndex: 900002, preis: '6.99', aenderungPreis: '0.01',
    bildUrl: 'http://localhost:18080/manual-test/cover.svg', mpUrl: '', istGelesen: true, istSpecial: false, reread: 1 };
  e.w.fetch = async url => { e.calls.push({ url }); return { json: async () => data }; };
  for (let i = 0; i < 2; i++) {
    e.w.closeEditBandPopup(); e.w.editBand(3); await settle();
    assert.equal(e.container.style.display, 'flex'); assert.equal(e.d.getElementById('manga_reihe_id').textContent, '3');
    assert.equal(e.d.getElementById('preis').value, '6,99'); assert.equal(e.d.getElementById('reread_band').value, '1');
    assert.equal(e.d.getElementById('ist_gelesen').checked, true);
    e.w.closeEditBandPopup(); assert.equal(e.container.style.display, 'none');
  }
  assert.equal(e.calls.length, 2);
});

test('Manuelle dynamische Bandcover laden lokal; Lazy Loading benötigt keinen MutationObserver', async t => {
  const e = await setup(t);
  e.d.body.dataset.coverPlaceholder = '/manual-test/cover.svg';
  let callback, target;
  e.w.IntersectionObserver = class { constructor(fn) { callback = fn; } observe(img) { target = img; } unobserve() {} };
  e.w.MutationObserver = class { constructor() { throw new Error('Veralteter Observer darf nicht starten'); } };
  const bands = e.d.createElement('div'); bands.id = 'bands-1'; bands.style.display = 'none'; e.d.body.append(bands);
  e.w.fetch = async url => { e.calls.push({ url }); return { json: async () => [{ id: 3, titel: 'Titel', bandNr: 1,
    bildUrl: 'https://upload.wikimedia.org/placeholder.png', mpUrl: '', totalPreis: '6,99' }] }; };
  e.evaluate('home.js'); e.d.dispatchEvent(new e.w.Event('DOMContentLoaded'));
  await e.w.toggleBands(1);
  assert.equal(bands.children.length, 1); assert.equal(bands.style.display, 'block');
  assert.equal(bands.firstElementChild.firstElementChild.getAttribute('src'), '/manual-test/cover.svg');
  callback([{ target, isIntersecting: true }], { unobserve() {} });
  assert.equal(target.getAttribute('src'), '/manual-test/cover.svg');
  target.dispatchEvent(new e.w.Event('load')); assert.equal(target.classList.contains('lazy'), false);
  e.w.toggleBands(1); assert.equal(bands.style.display, 'none');
  assert.equal(e.calls.length, 1);
});

for (const script of ['popup_add_reihe.js', 'popup_edit_reihe.js', 'einkaufsliste.js']) {
  test(`${script}: JSON-Erfolg schließt den Dialog und lädt genau einmal neu`, async t => {
    const e = await setup(t, script);
    await e.save(); assert.equal(e.calls.length, 1); assert.equal(e.parsed(), 1);
    assert.equal(e.container.style.display, 'none'); assert.equal(e.reloads.length, 1);
    assert.equal(e.button.disabled, true); // Keine zweite Speicherung vor dem Reload.
  });
}

test('Delete-200 ohne Body bewahrt den bisherigen leeren Erfolgsvertrag', async t => {
  const e = await setup(t, 'einkaufsliste.js', { status: 200 });
  e.d.querySelector('.delete-button').click(); await settle();
  assert.equal(e.calls.length, 1); assert.equal(e.calls[0].options.method, 'DELETE');
  assert.equal(e.parsed(), 0); assert.equal(e.reloads.length, 1);
});

test('Abgebrochene 417-Statusauswahl schreibt nicht und öffnet beim nächsten Klick wieder', async t => {
  const e = await setup(t, 'einkaufsliste.js', { status: 417, data: { message: 'Bitte Status für neue Reihe auswählen' } });
  const buy = e.d.querySelector('.buy-button'); buy.click(); await settle();
  e.d.getElementById('status').value = '';
  e.d.getElementById('confirmStatus').click(); await settle();
  assert.equal(e.calls.length, 1); assert.match(e.d.querySelector('.ask-for-status-popup .write-feedback').textContent, /Status/);
  e.d.getElementById('cancelStatus').click(); await settle();
  assert.equal(e.calls.length, 1); assert.equal(buy.disabled, false);
  buy.click(); await settle(); assert.equal(e.calls.length, 2);
  assert.equal(e.d.querySelector('.ask-for-status-popup').style.visibility, 'visible');
});

test('Unklarer Kauf-Create lässt Statusdialog offen; auch nach Cancel kein zweiter Kauf', async t => {
  const e = await setup(t, 'einkaufsliste.js', { status: 417, data: { message: 'Bitte Status für neue Reihe auswählen' } });
  const buy = e.d.querySelector('.buy-button'); buy.click(); await settle(); e.d.getElementById('status').value = '1';
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); throw new Error('private transport detail'); };
  const confirm = e.d.getElementById('confirmStatus'); confirm.click(); await settle(); confirm.click(); await settle();
  assert.equal(e.calls.length, 2); assert.equal(confirm.disabled, true);
  assert.equal(e.d.querySelector('.ask-for-status-popup').style.visibility, 'visible');
  assert.equal(e.d.getElementById('status').value, '1');
  e.d.getElementById('cancelStatus').click(); await settle(); buy.click(); await settle(); assert.equal(e.calls.length, 2);
});

// T03: Konflikte stammen ausschließlich aus der festen Header-Allowlist, nie aus Fehlerbodys.
for (const outcome of ['UNKNOWN_STATUS', 'AMBIGUOUS_SERIES', 'INCOMPATIBLE_SERIES', 'BAND_CONFLICT', 'INVALID_INVENTORY', 'BUSY']) {
  test(`Kauf-409 ${outcome}: fester Text, kein Replay und kein Fehlerbody`, async t => {
    const e = await setup(t, 'einkaufsliste.js', { status: 409, outcome, data: { message: 'private secret' } });
    const buy = e.d.querySelector('.buy-button'); buy.click(); await settle();
    assert.equal(e.calls.length, 1); assert.equal(e.parsed(), 0); assert.equal(e.textRead(), 0);
    assert.equal(e.reloads.length, 0); assert.equal(buy.disabled, false);
    const text = e.d.querySelector('main > .write-feedback').textContent;
    assert.doesNotMatch(text, /private|secret|SQLException/); assert.ok(text.length > 20);
    await settle(); assert.equal(e.calls.length, 1);
  });
}
for (const outcome of [undefined, '<img onerror=alert(1)>', '__proto__']) {
  test(`Unbekannter Kauf-409 ${outcome}: keine Bestätigung, bis Reload gesperrt`, async t => {
    const e = await setup(t, 'einkaufsliste.js', { status: 409, outcome });
    const buy = e.d.querySelector('.buy-button'); buy.click(); await settle(); buy.click(); await settle();
    assert.equal(e.calls.length, 1); assert.equal(buy.disabled, true);
    assert.equal(e.parsed(), 0); assert.equal(e.textRead(), 0); assert.equal(e.reloads.length, 0);
  });
}
const rangePreview = { message: 'Bitte Bände bestätigen', seriesId: '42', firstBand: 4, lastBand: 5 };
const purchaseResponse = (data, status = 200, outcome) => ({ status, ok: status >= 200 && status < 300,
  headers: new Headers({ 'Content-Type': 'application/json', ...(outcome ? { 'X-MangaLib-Purchase-Outcome': outcome } : {}) }),
  json: async () => data,
  text: async () => { throw new Error('Fehlerbody darf nicht gelesen werden'); } });

test('Bandbereich: Vorschau schreibt nicht erneut; Cancel erlaubt neue bewusste Vorschau', async t => {
  const e = await setup(t, 'einkaufsliste.js', { data: rangePreview });
  const buy = e.d.querySelector('.buy-button'); buy.click(); await settle();
  let popup = e.d.querySelector('.purchase-range-popup'); assert.ok(popup);
  assert.match(popup.textContent, /Bände 4–5/); assert.equal(e.calls.length, 1); assert.equal(buy.disabled, true);
  popup.querySelector('.ask-panel-cancel-button').click(); await settle();
  assert.equal(e.calls.length, 1); assert.equal(buy.disabled, false); assert.equal(e.d.querySelector('.purchase-range-popup'), null);
  buy.click(); await settle(); assert.equal(e.calls.length, 2); assert.ok(e.d.querySelector('.purchase-range-popup'));
});

test('Bandbereich: nur ausdrückliche Bestätigung sendet Serien-ID und Nummern mit CSRF genau einmal', async t => {
  const e = await setup(t, 'einkaufsliste.js', { data: rangePreview });
  e.d.querySelector('.buy-button').click(); await settle();
  let release;
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); return new Promise(r => { release = r; }); };
  const confirm = e.d.querySelector('.purchase-range-popup .ask-panel-confirm-button');
  confirm.click(); confirm.click(); await settle(); assert.equal(e.calls.length, 2);
  assert.deepEqual(JSON.parse(e.calls[1].options.body), { itemId: e.d.querySelector('.buy-button').dataset.id,
    confirmedSeriesId: '42', firstBand: 4, lastBand: 5 });
  assert.equal(new URL(e.calls[1].url).pathname, '/buyItemUpdate'); assert.equal(e.calls[1].options.headers.get('X-CSRF-TOKEN'), 'synthetic-token');
  release(purchaseResponse({ message: 'Reihe aktualisiert' })); await settle();
  assert.equal(e.reloads.length, 1); assert.equal(e.d.querySelector('.purchase-range-popup'), null);
});

test('Veralteter Bandbereich bleibt kontrollierter 409, kein automatischer Retry oder Reload', async t => {
  const e = await setup(t, 'einkaufsliste.js', { data: rangePreview });
  e.d.querySelector('.buy-button').click(); await settle();
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); return purchaseResponse(null, 409, 'BAND_CONFLICT'); };
  const popup = e.d.querySelector('.purchase-range-popup'); popup.querySelector('.ask-panel-confirm-button').click(); await settle();
  assert.equal(e.calls.length, 2); assert.equal(e.reloads.length, 0);
  assert.match(popup.querySelector('.write-feedback').textContent, /Bandbereich.*Bestand.*neu/);
  assert.equal(popup.querySelector('.ask-panel-confirm-button').disabled, false);
});

test('Unbestätigter Bandkauf bleibt nach 500 und Cancel bis Reload gesperrt', async t => {
  const e = await setup(t, 'einkaufsliste.js', { data: rangePreview });
  const buy = e.d.querySelector('.buy-button'); buy.click(); await settle();
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); return purchaseResponse(null, 500); };
  const popup = e.d.querySelector('.purchase-range-popup'), confirm = popup.querySelector('.ask-panel-confirm-button');
  confirm.click(); await settle(); confirm.click(); await settle(); assert.equal(e.calls.length, 2); assert.equal(confirm.disabled, true);
  popup.querySelector('.ask-panel-cancel-button').click(); await settle(); buy.click(); await settle();
  assert.equal(e.calls.length, 2); assert.equal(buy.disabled, true); assert.equal(e.reloads.length, 0);
});

test('Bereits gekauft zeigt feste Rückmeldung ohne Statusdialog oder Reload', async t => {
  const e = await setup(t, 'einkaufsliste.js', { data: { message: 'Kauf bereits übernommen' } });
  const buy = e.d.querySelector('.buy-button'); buy.click(); await settle(); buy.click(); await settle();
  assert.equal(e.calls.length, 1); assert.equal(e.reloads.length, 0); assert.equal(buy.disabled, true);
  assert.match(e.d.querySelector('main > .write-feedback').textContent, /bereits übernommen/);
  assert.equal(e.d.querySelector('.purchase-range-popup'), null);
});

test('Nach Statusauswahl erneut gefundene Reihe verlangt dieselbe ausdrückliche Bereichsbestätigung', async t => {
  const e = await setup(t, 'einkaufsliste.js', { status: 417, data: { message: 'Bitte Status für neue Reihe auswählen' } });
  e.d.querySelector('.buy-button').click(); await settle(); e.d.getElementById('status').value = '1';
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); return purchaseResponse(rangePreview); };
  e.d.getElementById('confirmStatus').click(); await settle();
  assert.equal(e.calls.length, 2); assert.ok(e.d.querySelector('.purchase-range-popup'));
  e.w.fetch = async (url, options) => { e.calls.push({ url, options }); return purchaseResponse({ message: 'Reihe aktualisiert' }); };
  e.d.querySelector('.purchase-range-popup .ask-panel-confirm-button').click(); await settle();
  assert.equal(e.calls.length, 3); assert.equal(new URL(e.calls[2].url).pathname, '/buyItemCreate');
  assert.equal(JSON.parse(e.calls[2].options.body).statusId, '1'); assert.equal(e.reloads.length, 1);
});

for (const preview of [{ ...rangePreview, seriesId: '<img>' }, { ...rangePreview, firstBand: 0 }, { ...rangePreview, lastBand: 10001 }, { message: 'unbekannter Erfolg' }]) {
  test(`Ungültige Kaufvorschau ${JSON.stringify(preview)} öffnet keinen Dialog`, async t => {
    const e = await setup(t, 'einkaufsliste.js', { data: preview });
    const buy = e.d.querySelector('.buy-button'); buy.click(); await settle(); buy.click(); await settle();
    assert.equal(e.calls.length, 1); assert.equal(buy.disabled, true);
    assert.equal(e.d.querySelector('.purchase-range-popup'), null); assert.equal(e.reloads.length, 0);
  });
}
