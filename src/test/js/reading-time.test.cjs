const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

test('Lesezeit umschalten verwendet sichere Textausgabe ohne Netzwerk', async () => {
  const dom = new JSDOM('<input type="checkbox" id="reading-time-rereads"><p id="reading-time-value"></p>',
    { runScripts: 'outside-only' });
  try {
    await new Promise(resolve => dom.window.document.addEventListener('DOMContentLoaded', resolve, { once: true }));
    const value = dom.window.document.getElementById('reading-time-value');
    value.dataset.first = '0 Tage 2 Std. 15 Min.';
    value.dataset.repeated = '<img src=x onerror=alert(1)>';
    value.textContent = value.dataset.first;
    dom.window.fetch = () => { throw new Error('Kein Netzwerk erlaubt'); };
    dom.window.eval(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/reading-time.js'), 'utf8'));
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
    const toggle = dom.window.document.getElementById('reading-time-rereads');
    assert.equal(toggle.checked, false);
    toggle.click();
    assert.equal(value.textContent, value.dataset.repeated);
    assert.equal(value.querySelector('img'), null);
    toggle.click();
    assert.equal(value.textContent, '0 Tage 2 Std. 15 Min.');
  } finally { dom.window.close(); }
});
