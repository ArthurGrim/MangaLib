const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

async function render(data, ok = true, details = false) {
  const dom = new JSDOM('<div id="distributions-grid"></div><p id="distributions-status"></p>' +
    (details ? '<div id="statistics-details-grid"></div><p id="statistics-details-status"></p>' : ''),
    { runScripts: 'outside-only' });
  await new Promise(resolve => dom.window.document.addEventListener('DOMContentLoaded', resolve, { once: true }));
  dom.window.fetch = async url => {
    assert.ok(['/statistik/verteilungen', '/statistik/weitere'].includes(url));
    return { ok, json: async () => data };
  };
  dom.window.eval(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/statistics-distributions.js'), 'utf8'));
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  for (let i = 0; i < 4; i++) await new Promise(resolve => setImmediate(resolve));
  return dom;
}

test('Verteilungen zeigen unbekannte Merkmale, Prozentwerte und sichere Texte', async () => {
  const dom = await render([{ title: 'Typ', unit: 'Reihen',
    counts: { '<img src=x onerror=alert(1)>': 1, Unbekannt: 3 } },
    { title: 'Format', unit: 'Reihen', counts: {} }]);
  try {
    const d = dom.window.document;
    assert.equal(d.querySelectorAll('article').length, 2);
    assert.equal(d.querySelector('img'), null);
    assert.match(d.querySelector('ul').textContent, /25 %/);
    assert.match(d.querySelector('ul').textContent, /75 %/);
    assert.match(d.body.textContent, /Keine Reihen vorhanden/);
    assert.equal(d.querySelector('.distribution-pie').getAttribute('aria-hidden'), 'true');
    assert.equal(d.getElementById('distributions-status').textContent, '');
  } finally { dom.window.close(); }
});

for (const data of [null, [{ title: 'Typ', unit: 'Reihen', counts: { Manga: -1 } }]]) {
  test('Ungültige Verteilungen ergeben eine sichtbare Fehlermeldung', async () => {
    const dom = await render(data);
    try {
      assert.match(dom.window.document.body.textContent, /nicht geladen/);
      assert.equal(dom.window.document.querySelectorAll('article').length, 0);
    } finally { dom.window.close(); }
  });
}

test('Rangliste sortiert nach Anzahl und zeigt messbare Anteile', async () => {
  const dom = await render([{ title: 'Verlage', unit: 'Reihen', counts: { Klein: 1, Groß: 3 } }], true, true);
  try {
    const grid = dom.window.document.getElementById('statistics-details-grid');
    assert.match(grid.querySelector('li').textContent, /Groß: 3/);
    assert.equal(grid.querySelector('meter').value, 3);
    assert.equal(grid.querySelector('meter').max, 4);
    assert.equal(grid.querySelector('.distribution-pie'), null);
  } finally { dom.window.close(); }
});
