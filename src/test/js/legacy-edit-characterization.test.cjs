const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { JSDOM, VirtualConsole } = require('jsdom');
const root = path.resolve(__dirname, '../../main/resources');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const settle = async () => { for (let i = 0; i < 4; i++) await new Promise(r => setImmediate(r)); };

// Regressionen der freigegebenen Soll-Regeln. Historische Ist-Befunde in TESTING.md.
// Keine Ressourcen laden,
// kein Browser, keine Datenbank. Ein DOM-src-Attribut beweist keinen realen Netzwerkabruf.
async function dialog(t, kind, overrides = {}, readStatus = 200) {
  const resources = [], errors = [], calls = [];
  const vc = new VirtualConsole(); vc.on('jsdomError', error => errors.push(error));
  const dom = new JSDOM(read('templates/home.html').replace(/<link\b[^>]*>/g, ''), {
    url: 'http://localhost:18080/home', runScripts: 'outside-only', virtualConsole: vc,
    resources: { dispatcher: { dispatch(options) { resources.push(options.origin); throw new Error('Netzwerk gesperrt'); } } }
  });
  const w = dom.window, d = w.document;
  t.after(() => { w.close(); assert.deepEqual(resources, []); assert.deepEqual(errors, []); });
  await new Promise(resolve => d.addEventListener('DOMContentLoaded', resolve, { once: true }));
  w.Headers = Headers; w.console = { log() {}, error() {} };
  w.alert = () => { throw new Error('Kein nativer Alert'); };
  w.XMLHttpRequest = w.WebSocket = class { constructor() { throw new Error('Netzwerk gesperrt'); } };
  w.setTimeout = () => 0;
  for (const [name, value] of [['_csrf', 'synthetic-token'], ['_csrf_header', 'X-CSRF-TOKEN']])
    d.querySelector(`meta[name="${name}"]`).content = value;
  for (const id of ['status', 'verlag', 'typ', 'format', 'sammelband_typ'])
    d.getElementById(id + '-edit').append(new w.Option('Synthetisch', '1'));
  const data = kind === 'series'
    ? { id: 42, titel: 'Altbestand', statusId: 1, verlagId: 1, typId: 1, formatId: 1,
        anzahlBaende: 1, preisProBand: '6,99', istGelesen: false, reread: 0,
        istVergriffen: null, istEbayPreis: null, anilistUrl: null, coverUrl: null, ...overrides }
    : { id: 11, titel: 'Altbestand', bandNr: 1, bandIndex: '', preis: '6.99', aenderungPreis: '0.00',
        istGelesen: false, istSpecial: false, reread: 0, bildUrl: '', mpUrl: '', ...overrides };
  w.fetch = async (url, options) => {
    calls.push({ url, options });
    if (!options) return { ok: readStatus < 400, json: async () => data };
    // Kontrollierte Ablehnung verhindert Navigation/Reload; geprüft wird der tatsächlich erzeugte Body.
    return { ok: false, status: 400 };
  };
  const script = kind === 'series' ? 'popup_edit_reihe.js' : 'popup_edit_baende.js';
  for (const file of ['security.js', script]) vm.runInContext(read('static/js/' + file), dom.getInternalVMContext());
  if (kind === 'series') {
    d.dispatchEvent(new w.Event('DOMContentLoaded'));
    d.querySelector('.edit-button').setAttribute('data-id', '42'); d.querySelector('.edit-button').click();
  } else w.editBand(11);
  await settle();
  const scope = d.getElementById(kind === 'series' ? 'editPopupContainer' : 'editBaendePopupContainer');
  assert.equal(scope.style.display, 'flex');
  // outside-only führt absichtlich keine Inline-Handler aus. Genau den Template-
  // Handler anbinden, ohne fremde Seitenskripte oder runScripts: dangerously.
  if (kind === 'band') {
    const button = scope.querySelector('.save-button-edit');
    assert.equal(button.getAttribute('onclick'), 'saveBand()');
    button.addEventListener('click', () => w.saveBand());
  }
  const save = async () => { scope.querySelector('button').click(); await settle(); };
  const payload = () => {
    const writes = calls.filter(c => c.options); assert.equal(writes.length, 1);
    assert.equal(writes[0].options.headers.get('X-CSRF-TOKEN'), 'synthetic-token');
    return JSON.parse(writes[0].options.body);
  };
  return { w, d, calls, scope, save, payload };
}

for (const reread of [0, 3]) {
  test(`Banddialog erhält angezeigten Reread ${reread} bei reiner Spezialflag-Änderung`, async t => {
    const e = await dialog(t, 'band', { reread });
    e.d.getElementById('ist_spezial').checked = true; await e.save();
    assert.equal(e.payload().reread, String(reread)); assert.equal(e.payload().istSpecial, true);
  });
}

test('Banddialog erhält NULL-Spezialflag mit additiver GET-Information', async t => {
  const e = await dialog(t, 'band', { istSpecial: false, specialNull: true }); await e.save();
  assert.equal(e.payload().istSpecial, null);
});

for (const flag of [null, false, true]) {
  test(`Reihendialog erhält unberührte Flags ${flag} bei Titeländerung`, async t => {
    const e = await dialog(t, 'series', { istVergriffen: flag, istEbayPreis: flag });
    e.d.getElementById('titel-edit').value = 'Nur Titel geändert'; await e.save();
    assert.equal(e.payload().istVergriffen, flag); assert.equal(e.payload().istEbayPreis, flag);
    assert.equal(e.payload().anzahlBaende, 1);
  });
}

for (const value of [null, '', 'http://localhost:18080/synthetic/old']) {
  test(`Reihendialog unterscheidet unberührte optionale URL ${value}`, async t => {
    const e = await dialog(t, 'series', { anilistUrl: value }); await e.save();
    assert.equal(e.payload().anilistUrl, value);
  });
}

for (const value of ['', 'http://localhost:18080/synthetic/new']) {
  test(`Bewusstes Leeren/Neusetzen einer Reihen-URL erzeugt ${JSON.stringify(value)}`, async t => {
    const e = await dialog(t, 'series', { anilistUrl: 'http://localhost:18080/synthetic/old' });
    e.d.getElementById('anilist_url-edit').value = value; await e.save();
    assert.equal(e.payload().anilistUrl, value);
  });
}

for (const value of ['/legacy/path', 'file:///synthetic/cover.png']) {
  test(`Unveränderte historische Reihen-URL ${value} wird zum serverseitigen Vergleich gesendet`, async t => {
    const e = await dialog(t, 'series', { anilistUrl: value });
    e.d.getElementById('titel-edit').value = 'Nur Titel geändert'; await e.save();
    assert.equal(e.payload().anilistUrl, value);
    assert.match(e.scope.querySelector('.write-feedback').textContent, /ungültig/); // HTTP-Double bleibt 400.
    assert.equal(e.scope.style.display, 'flex');
  });
}

test('IST: URL mit Userinfo passiert Frontend und wird erst serverseitig abgewiesen', async t => {
  const e = await dialog(t, 'band', { bildUrl: 'https://synthetic@example.invalid/old.png' });
  await e.save(); assert.equal(e.payload().bildUrl, 'https://synthetic@example.invalid/old.png');
  assert.match(e.scope.querySelector('.write-feedback').textContent, /ungültig/);
});

for (const value of [null, undefined]) {
  test(`Preis ${value} bleibt unbekannt, ohne NaN oder erfundenen Nullpreis`, async t => {
    const e = await dialog(t, 'band', { preis: value });
    assert.equal(e.d.getElementById('preis').value, ''); await e.save();
    assert.equal(e.payload().preis, null);
  });
}

test('NULL-Preisanpassung bleibt unbekannt', async t => {
  const e = await dialog(t, 'band', { aenderungPreis: null }); await e.save();
  assert.equal(e.payload().aenderungPreis, null);
});

test('Negative Preisanpassung und vorhandener Preis bleiben im Formular erhalten', async t => {
  const e = await dialog(t, 'band', { aenderungPreis: '-0.02' }); await e.save();
  assert.equal(e.payload().preis, '6.99'); assert.equal(e.payload().aenderungPreis, '-0.02');
});

test('IST: Fehlende Pflichtreferenz verhindert selbst eine reine Titeländerung', async t => {
  const e = await dialog(t, 'series', { verlagId: null });
  e.d.getElementById('titel-edit').value = 'Nur Titel geändert'; await e.save();
  assert.equal(e.calls.length, 1); assert.match(e.scope.querySelector('.write-feedback').textContent, /Verlag/);
});

for (const value of ['file:///synthetic/old.png', 'https://synthetic@example.invalid/old.png']) {
  test(`Historische unsichere Bild-URL erhält beim Dialogöffnen einen lokalen Platzhalter: ${value}`, async t => {
    const e = await dialog(t, 'band', { bildUrl: value });
    assert.equal(e.d.getElementById('editBandCoverImage').getAttribute('src'), '/svg/filler_img2.png');
    assert.equal(e.d.getElementById('bild_url').value, value, 'Gespeicherte Eingabe nicht normalisieren');
    assert.equal(e.calls.length, 1);
    // Keine reale Ressource laden; Eingabefeld und sicherer Bildsink sind getrennt.
  });
}

test('Unberührte unbekannte Band-Rereads und Spezialflags bleiben NULL', async t => {
  const e = await dialog(t, 'band', { reread: 0, rereadNull: true, specialNull: true });
  assert.equal(e.d.getElementById('reread_band').value, ''); await e.save();
  assert.equal(e.d.getElementById('ist_spezial').indeterminate, true);
  assert.equal(e.payload().reread, null); assert.equal(e.payload().istSpecial, null);
});

for (const kind of ['series', 'band']) {
  test(`Ladefehler bei ${kind} zeigt neutrale Meldung und verhindert Schreiben veralteter Werte`, async t => {
    const e = await dialog(t, kind, {}, 400);
    assert.equal(e.scope.querySelector('.save-button-edit').disabled, true);
    assert.match(e.scope.querySelector('.write-feedback').textContent, /konnte nicht geladen werden/);
    await e.save(); assert.equal(e.calls.length, 1);
  });
}

test('Bewusstes Setzen von unbekanntem Reread auf 0 und Spezialflag auf false bleibt möglich', async t => {
  const e = await dialog(t, 'band', { rereadNull: true, specialNull: true });
  e.d.getElementById('reread_band').value = '0';
  const special = e.d.getElementById('ist_spezial');
  special.checked = true; special.dispatchEvent(new e.w.Event('change'));
  special.checked = false; special.dispatchEvent(new e.w.Event('change')); await e.save();
  assert.equal(e.payload().reread, '0'); assert.equal(e.payload().istSpecial, false);
});

test('Massenoptionen sind standardmäßig aus und nur explizit im Payload aktiv', async t => {
  const e = await dialog(t, 'series');
  assert.equal(e.d.getElementById('apply-read-edit').checked, false);
  assert.equal(e.d.getElementById('apply-reread-edit').checked, false);
  e.d.getElementById('apply-read-edit').checked = true;
  e.d.getElementById('apply-reread-edit').checked = true;
  e.d.getElementById('reread-edit').value = '0';
  e.d.getElementById('reread-edit').dispatchEvent(new e.w.Event('input')); await e.save();
  assert.equal(e.payload().applyReadToVolumes, true); assert.equal(e.payload().applyRereadToVolumes, true);
  assert.equal(e.payload().rereadEdited, true);
  e.d.querySelector('.cancel-button-edit').click(); e.d.querySelector('.edit-button').click(); await settle();
  assert.equal(e.d.getElementById('apply-read-edit').checked, false);
  assert.equal(e.d.getElementById('apply-reread-edit').checked, false);
});

test('Neue ungültige URL bleibt schon im Dialog abgewiesen', async t => {
  const e = await dialog(t, 'band'); e.d.getElementById('bild_url').value = 'file:///synthetic/new.png';
  await e.save(); assert.equal(e.calls.length, 1);
  assert.match(e.scope.querySelector('.write-feedback').textContent, /gültige Bild-URL/);
});

for (const value of ['javascript:alert(1)', 'data:image/svg+xml,x', '//example.invalid/x', '/old.png',
  'https://user:synthetic@example.invalid/x', 'http://example.invalid:65536/x', 'http://example.invalid/a\\b']) {
  test(`Gemeinsame Bildzuweisung verwendet sicheren Platzhalter für ${value}`, async t => {
    const e = await dialog(t, 'band', { bildUrl: value });
    assert.equal(e.d.getElementById('editBandCoverImage').getAttribute('src'), '/svg/filler_img2.png');
    assert.equal(e.d.getElementById('bild_url').value, value);
  });
}
