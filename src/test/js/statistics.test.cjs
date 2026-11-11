const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const settle = async () => { for (let i = 0; i < 4; i++) await new Promise(resolve => setImmediate(resolve)); };

test('Monatsdiagramm verarbeitet wiederholte Wechsel, Centbeträge und veraltete Antworten', async () => {
  const dom = new JSDOM(`<div class="bar-chart-wrap"></div><div class="x-axis"></div>
    <select id="yearSelector"></select><button id="optionBands"></button><button id="optionSpending"></button>
    <svg class="line-chart"></svg><div class="x-axis-pro-monat"></div><p id="monthly-chart-status"></p>`,
    { runScripts: 'outside-only' });
  try {
    const w = dom.window, d = w.document, calls = [];
    await new Promise(resolve => d.addEventListener('DOMContentLoaded', resolve, { once: true }));
    w.console.log = () => {};
    w.fetch = url => {
      if (url.endsWith('bandKategorien')) return Promise.resolve({ json: async () => ({}) });
      return new Promise(resolve => calls.push({ url, resolve }));
    };
    w.eval(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/statistik.js'), 'utf8'));
    d.dispatchEvent(new w.Event('DOMContentLoaded'));
    assert.equal(calls.length, 1, 'nur ein initialer Monatsrequest');
    const year = d.getElementById('yearSelector');
    const current = Number(year.value);
    year.value = String(current - 1); year.dispatchEvent(new w.Event('change'));
    year.value = String(current); year.dispatchEvent(new w.Event('change'));
    d.getElementById('optionSpending').click();
    d.getElementById('optionBands').click();
    d.getElementById('optionSpending').click();
    assert.equal(calls.length, 6);
    calls[5].resolve({ ok: true, json: async () => ({ 1: 0.01, 12: -0.02 }) });
    await settle();
    const labels = () => [...d.querySelectorAll('.value-label')].map(e => e.textContent);
    assert.match(labels()[0], /0,01\s*€/);
    assert.match(labels()[11], /-0,02\s*€/);
    assert.equal(d.querySelectorAll('.point').length, 12);
    for (const point of d.querySelectorAll('.point')) {
      assert.ok(Number.isFinite(Number(point.getAttribute('cy'))));
      assert.ok(Number(point.getAttribute('cy')) >= 0 && Number(point.getAttribute('cy')) <= 280);
    }
    calls[0].resolve({ ok: true, json: async () => ({ 1: 999 }) });
    await settle();
    assert.match(labels()[0], /0,01/);
    d.getElementById('optionBands').click();
    calls[6].resolve({ ok: false });
    await settle();
    assert.equal(d.querySelectorAll('.point').length, 0);
    assert.match(d.getElementById('monthly-chart-status').textContent, /nicht geladen/);
    d.getElementById('optionSpending').click();
    calls[7].resolve({ ok: true, json: async () => ({}) });
    await settle();
    assert.equal(d.querySelectorAll('.point').length, 12);
    assert.equal(d.getElementById('monthly-chart-status').textContent, '');
  } finally { dom.window.close(); }
});
