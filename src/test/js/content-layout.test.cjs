const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM, VirtualConsole } = require('jsdom');
const root = path.resolve(__dirname, '../../main/resources');
const css = fs.readFileSync(path.join(root, 'static/css/content-layout.css'), 'utf8');

for (const page of ['home', 'einkaufsliste']) {
  test(`${page}: gezielte Layoutregeln werden nach dem bisherigen Stylesheet geladen`, t => {
    const html = fs.readFileSync(path.join(root, `templates/${page}.html`), 'utf8');
    const dom = new JSDOM(html, { runScripts: 'outside-only', virtualConsole: new VirtualConsole() });
    t.after(() => dom.window.close());
    const links = [...dom.window.document.querySelectorAll('link[rel="stylesheet"]')];
    assert.equal(links.filter(e => e.getAttribute('href') === '/css/content-layout.css').length, 1);
    assert.ok(links.findIndex(e => e.getAttribute('href') === '/css/content-layout.css') >
      links.findIndex(e => e.getAttribute('th:href') === `@{/css/${page}.css}`));
  });
}

function rules(t) {
  const dom = new JSDOM('<style></style>', { runScripts: 'outside-only' });
  t.after(() => dom.window.close());
  dom.window.document.querySelector('style').textContent = css;
  const sheet = [...dom.window.document.querySelector('style').sheet.cssRules];
  const find = (list, selector) => list.find(r => r.selectorText === selector);
  return { dom, sheet, find };
}

test('Übersichtsgrids erlauben schrumpfende Textspalten und reservieren Aktionsspalten', t => {
  const { sheet, find } = rules(t);
  for (const selector of ['.manga-reihen-header, .mangaReihe-container', '.band-container', '.reihe']) {
    const columns = find(sheet, selector).style.getPropertyValue('grid-template-columns');
    assert.match(columns, /minmax\(0,/); assert.match(columns, /30px/);
  }
  const title = find(sheet, '.titel p, .band-container p, .manga-reihe-titel, .title-label').style;
  assert.equal(title.getPropertyValue('overflow-wrap'), 'anywhere');
  assert.equal(title.getPropertyValue('white-space'), 'normal');
  assert.equal(title.getPropertyValue('min-width'), '0px');
});

test('Dialogheader trennt Titel von Save; lange Dialoge bleiben vertikal scrollbar', t => {
  const { sheet, find } = rules(t);
  const header = find(sheet, '.popup-header').style;
  assert.equal(header.getPropertyValue('display'), 'grid');
  assert.equal(header.getPropertyValue('grid-template-columns'), '100px minmax(0, 1fr) max-content');
  const panel = find(sheet, '.popup, .popup-edit, .popup-edit-band, .ask-panel').style;
  assert.equal(panel.getPropertyValue('overflow-y'), 'auto');
  assert.match(panel.getPropertyValue('max-height'), /100dvh.*manual-banner-height/);
  assert.match(panel.getPropertyValue('max-width'), /100vw/);
});

test('Schmale Breiten entfernen Sidebar-Überlagerung und platzieren Save unter dem Titel', t => {
  const { sheet, find } = rules(t);
  const narrow = [...sheet.find(r => r.conditionText === '(max-width: 1100px)').cssRules];
  assert.equal(find(narrow, 'body .sidebar').style.getPropertyValue('position'), 'static');
  assert.equal(find(narrow, '.main-menu').style.getPropertyValue('position'), 'static');
  const mobile = [...sheet.find(r => r.conditionText === '(max-width: 480px)').cssRules];
  assert.equal(find(mobile, '.popup-header .save-button-edit').style.getPropertyValue('grid-column'), '1 / -1');
  assert.equal(find(mobile, '.popup-header').style.getPropertyValue('grid-template-columns'), '64px minmax(0, 1fr)');
});

test('Lange Unicode-/HTML-Titel bleiben im DOM vollständig lesbarer Text neben den Buttons', t => {
  const { dom } = rules(t), d = dom.window.document;
  const title = `T02_BROWSER_ACCEPTANCE_20261014_${'X'.repeat(180)} ÄÖÜ 漫画 & <img onerror="alert(1)">`;
  const header = d.createElement('div'); header.className = 'popup-header';
  const cover = d.createElement('div'); cover.className = 'cover-bild-edit';
  const label = d.createElement('label'); label.className = 'title-label'; label.textContent = title;
  const save = d.createElement('button'); save.className = 'save-button-edit'; save.textContent = 'Save';
  header.append(cover, label, save); d.body.append(header);
  assert.equal(label.textContent, title); assert.equal(label.children.length, 0);
  assert.equal(header.children.length, 3); assert.equal(save.textContent, 'Save');
  assert.equal(dom.window.getComputedStyle(header).display, 'grid');
  assert.equal(dom.window.getComputedStyle(label).overflowWrap, 'anywhere');
  // jsdom hat kein Pixel-Layout: tatsächliche Kollisionen/Scrollbreiten später im Browser prüfen.
});
