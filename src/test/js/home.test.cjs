const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { JSDOM } = require('jsdom');

const homeSource = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/home.js'), 'utf8');
const fixture = {
    id: 42, titel: 'Manga & Freunde <3', bandNr: 2, totalPreis: '6,99 €',
    bildUrl: 'https://media.manga-passion.de/cover.jpg',
    mpUrl: 'https://www.manga-passion.de/volumes/123', istGelesen: true
};

function setup(t, { intersectionObserver = true } = {}) {
    const externalRequests = [];
    // Echtes DOM unter Node, aber keine Inline-/Seitenskripte, Browser oder Netzwerkzugriffe.
    const dom = new JSDOM('<div id="bands-1" style="display:none"></div><div id="bands-2" style="display:none"></div>', {
        url: 'http://localhost:8080/home', runScripts: 'outside-only',
        resources: { dispatcher: {
            dispatch(options) {
                externalRequests.push(options.origin);
                throw new Error('Externe Ressourcen sind in DOM-Tests gesperrt.');
            }
        } }
    });
    const { window } = dom;
    const opened = [], edited = [], requests = [], observers = [];
    let response = [fixture];
    window.console = { log() {}, error() {} };
    window.XMLHttpRequest = window.WebSocket = class {
        constructor() { throw new Error('Netzwerkzugriff in DOM-Tests gesperrt.'); }
    };
    window.fetch = async url => {
        assert.match(url, /^\/mangaReihe\/[12]\/bands$/);
        requests.push(url);
        return { json: async () => response };
    };
    window.open = (...args) => opened.push(args);
    window.editBand = id => edited.push(id);
    if (intersectionObserver) {
        window.IntersectionObserver = class {
            constructor(callback) { this.callback = callback; this.images = []; observers.push(this); }
            observe(image) { this.images.push(image); }
            unobserve(image) { this.unobserved = image; }
        };
    }
    vm.runInContext(homeSource, dom.getInternalVMContext(), { filename: 'home.js' });
    t.after(() => {
        window.close();
        assert.deepEqual(externalRequests, [], 'DOM-Prüfungen dürfen keine Ressourcen abrufen.');
    });
    return {
        window, document: window.document, opened, edited, requests, observers,
        setResponse(value) { response = value; }
    };
}

test('Normale Bandanzeige erhält DOM-Reihenfolge, CSS, Texte, Icons und Ereignisse', async t => {
    const env = setup(t);
    await env.window.toggleBands(1);
    const container = env.document.getElementById('bands-1');
    const row = container.firstElementChild;
    assert.equal(container.style.display, 'block');
    assert.equal(container.children.length, 1);
    assert.equal(row.className, 'band-container');
    assert.deepEqual([...row.children].map(node => node.tagName), ['IMG', 'P', 'P', 'BUTTON', 'DIV', 'BUTTON']);
    assert.equal(row.children[0].className, 'lazy');
    assert.equal(row.children[0].getAttribute('src'), '/svg/filler_img2.png');
    assert.equal(row.children[0].dataset.src, fixture.bildUrl);
    assert.equal(row.children[0].alt, 'Band Bild');
    assert.equal(row.children[1].textContent, `${fixture.titel}, Band: 2`);
    assert.equal(row.children[2].textContent, '6,99 €');
    assert.equal(row.children[3].className, 'MP-link');
    assert.equal(row.children[3].firstElementChild.getAttribute('src'), '/svg/MangaPassion_logo.png');
    assert.equal(row.children[3].firstElementChild.alt, 'MP');
    assert.equal(row.children[3].firstElementChild.style.height, '17px');
    assert.equal(row.children[3].firstElementChild.style.width, '17px');
    assert.equal(row.children[4].className, 'istGelesen-label checked');
    assert.equal(row.children[5].className, 'edit-band-button');
    assert.equal(row.children[5].dataset.id, '42');
    assert.equal(row.children[5].firstElementChild.getAttribute('src'), '/svg/edit_icon.svg');
    assert.equal(row.children[5].firstElementChild.alt, 'Edit');
    assert.equal(row.children[5].firstElementChild.style.width, '17px');
    row.children[3].click();
    row.children[5].click();
    assert.deepEqual(env.opened, [[fixture.mpUrl, '_blank', 'noopener,noreferrer']]);
    assert.deepEqual(env.edited, ['42']);
    assert.deepEqual(env.requests, ['/mangaReihe/1/bands']);
    assert.equal(row.querySelector('[onclick]'), null);
});

for (const title of ['<img src=x onerror="alert(1)">', '<script>alert(1)</script>', '" autofocus onfocus="alert(1)', "O'Brien & <Freunde> – 漫画"]) {
    test(`Titel bleibt unveränderter Text: ${title}`, t => {
        const env = setup(t);
        const row = env.window.createBandElement({ ...fixture, titel: title });
        assert.equal(row.children[1].textContent, `${title}, Band: 2`);
        assert.equal(row.children[1].children.length, 0);
        assert.equal(row.querySelectorAll('img').length, 3);
        assert.equal(row.querySelector('script, [onerror], [onfocus], [onclick]'), null);
    });
}

test('Benachbarte Preis-, Bandnummer- und ID-Werte erzeugen weder Markup noch Eventattribute', t => {
    const env = setup(t);
    const injected = '<img src=x onerror="alert(1)">';
    const row = env.window.createBandElement({ ...fixture, bandNr: injected, totalPreis: injected, id: '42" onclick="alert(1)' });
    assert.equal(row.children[1].textContent, `${fixture.titel}, Band: ${injected}`);
    assert.equal(row.children[2].textContent, injected);
    assert.equal(row.children[5].dataset.id, '42" onclick="alert(1)');
    assert.equal(row.querySelector('script, [onerror], [onclick]'), null);
    assert.equal(row.querySelectorAll('img').length, 3);
});

test('Angriffsstrings aus der Band-JSON-Antwort bleiben auch im vollständigen Ladepfad inert', async t => {
    const env = setup(t);
    const title = '<img src=x onerror="alert(1)"><script>alert(1)</script>';
    const url = "https://www.manga-passion.de/volumes/1');alert(1);//";
    env.setResponse([{ ...fixture, titel: title, mpUrl: url }]);
    await env.window.toggleBands(1);
    const row = env.document.getElementById('bands-1').firstElementChild;
    assert.equal(row.children[1].textContent, `${title}, Band: 2`);
    assert.equal(row.children[1].children.length, 0);
    assert.equal(row.querySelector('script, [onerror], [onclick]'), null);
    row.querySelector('.MP-link').click();
    assert.deepEqual(env.opened, [[url, '_blank', 'noopener,noreferrer']]);
});

for (const url of ["https://www.manga-passion.de/volumes/1');alert(1);//", 'https://www.manga-passion.de/volumes/1"onerror="alert(1)']) {
    test(`URL-Sonderzeichen bleiben URL-Werte: ${url}`, t => {
        const env = setup(t);
        const row = env.window.createBandElement({ ...fixture, bildUrl: url, mpUrl: url });
        const expected = new URL(url).href;
        assert.equal(row.children[0].dataset.src, expected);
        assert.equal(row.querySelector('[onclick], [onerror]'), null);
        assert.equal(row.querySelectorAll('img').length, 3);
        row.children[3].click();
        assert.deepEqual(env.opened, [[expected, '_blank', 'noopener,noreferrer']]);
    });
}

const invalidUrls = [
    'javascript:alert(1)', 'JaVaScRiPt:alert(1)', 'data:image/svg+xml,<svg onload="alert(1)"/>',
    'file:///C:/private.txt', 'blob:https://example.invalid/id', 'ftp://example.invalid/file',
    '/relative.jpg', '//example.invalid/file', 'https://', 'https:example.invalid', 'https:/example.invalid',
    'kein-link', '', null, 123,
    'https://user:password@example.invalid/file', 'https://example.invalid/\nfile',
    'https://example.invalid/ file', 'https:\\example.invalid\\file'
];
for (const [index, url] of invalidUrls.entries()) {
    test(`Unsichere oder ungültige URL ${index + 1} wird nicht verwendet`, t => {
        const env = setup(t);
        const row = env.window.createBandElement({ ...fixture, bildUrl: url, mpUrl: url });
        assert.equal(row.children[0].getAttribute('src'), '/svg/filler_img2.png');
        assert.equal(row.children[0].hasAttribute('data-src'), false);
        assert.equal(row.children[3].disabled, true);
        row.children[3].click();
        assert.deepEqual(env.opened, []);
    });
}

test('Gültige HTTP-/HTTPS-Cover und Links bleiben nutzbar', t => {
    const env = setup(t);
    for (const url of ['http://media.manga-passion.de/cover.jpg', 'https://example.invalid/cover.jpg?size=large#image']) {
        assert.equal(env.window.getSafeBandUrl(url), url);
        const row = env.window.createBandElement({ ...fixture, bildUrl: url, mpUrl: url, istGelesen: false });
        assert.equal(row.children[0].dataset.src, url);
        assert.equal(row.children[3].disabled, false);
        assert.equal(row.children[4].className, 'istGelesen-label');
    }
});

test('Ein-/Ausklappen ersetzt alte Knoten; andere Reihen erhalten keine doppelten Listener', async t => {
    const env = setup(t);
    await env.window.toggleBands(1);
    await env.window.toggleBands(2);
    const firstRow = env.document.getElementById('bands-1').firstElementChild;
    firstRow.querySelector('.edit-band-button').click();
    assert.deepEqual(env.edited, ['42']);
    env.window.toggleBands(1);
    assert.equal(env.document.getElementById('bands-1').style.display, 'none');
    assert.equal(env.requests.length, 2);
    env.setResponse([{ ...fixture, id: 43 }]);
    await env.window.toggleBands(1);
    assert.equal(firstRow.isConnected, false);
    const current = env.document.getElementById('bands-1');
    assert.equal(current.children.length, 1);
    current.querySelector('.edit-band-button').click();
    assert.deepEqual(env.edited, ['42', '43']);
});

test('Leere Bandantwort leert den Container', async t => {
    const env = setup(t);
    env.document.getElementById('bands-1').append(env.document.createElement('p'));
    env.setResponse([]);
    await env.window.toggleBands(1);
    assert.equal(env.document.getElementById('bands-1').children.length, 0);
});

test('Lazy Loading prüft URLs auch unmittelbar vor IntersectionObserver-Zuweisung', t => {
    const env = setup(t);
    const row = env.window.createBandElement(fixture);
    env.document.getElementById('bands-1').append(row);
    env.window.initializeLazyLoading();
    const observer = env.observers.at(-1);
    const cover = row.firstElementChild;
    observer.callback([{ target: cover, isIntersecting: true }], observer);
    assert.equal(cover.src, fixture.bildUrl);
    assert.equal(observer.unobserved, cover);
    cover.dispatchEvent(new env.window.Event('load'));
    assert.equal(cover.classList.contains('lazy'), false);
    cover.dataset.src = 'javascript:alert(1)';
    observer.callback([{ target: cover, isIntersecting: true }], observer);
    assert.equal(cover.getAttribute('src'), '/svg/filler_img2.png');
});

test('Lazy-Loading-Fallback verwendet sichere URLs und Platzhalter ohne Netzwerk', t => {
    const env = setup(t, { intersectionObserver: false });
    const callbacks = [];
    env.window.setTimeout = callback => { callbacks.push(callback); return callbacks.length; };
    env.window.clearTimeout = () => {};
    const valid = env.window.createBandElement(fixture).firstElementChild;
    const invalid = env.window.createBandElement({ ...fixture, bildUrl: 'data:text/html,unsafe' }).firstElementChild;
    env.document.getElementById('bands-1').append(valid, invalid);
    env.window.initializeLazyLoading();
    env.document.dispatchEvent(new env.window.Event('scroll'));
    callbacks.forEach(callback => callback());
    assert.equal(valid.src, fixture.bildUrl);
    assert.equal(invalid.getAttribute('src'), '/svg/filler_img2.png');
    assert.equal(valid.classList.contains('lazy'), false);
});
