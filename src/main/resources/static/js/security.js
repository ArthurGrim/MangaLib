/* Session-CSRF aus der gerenderten Seite; keine Token-URLs, fremden Ziele oder Schreib-Replays. */
async function mangaLibFetch(path, options = {}) {
  const fail = (message) => {
    const error = new Error(message);
    error.status = 403;
    throw error;
  };
  const url = new URL(path, window.location.href);
  if (url.origin !== window.location.origin || url.username || url.password) {
    return fail("Die Anfrage darf nur an MangaLib auf dieser Seite gesendet werden.");
  }
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers);
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (!token || !header) {
      return fail("Sicherheitstoken fehlt. Bitte Seite neu laden und die Aktion erneut ausführen.");
    }
    headers.set(header, token);
  }
  // Die geprüfte absolute URL verwenden: auch ein <base>-Element darf das Ziel nicht verändern.
  const response = await fetch(url.href, { ...options, method, headers, credentials: "same-origin", mode: "same-origin", redirect: "error" });
  if (response.status === 403) {
    return fail("Anfrage aus Sicherheitsgründen abgewiesen. Bitte Seite neu laden und die Aktion erneut ausführen.");
  }
  return response;
}

// Nur feste Meldungen anzeigen; HTTP-Fehlerbodys werden absichtlich nicht gelesen.
const MangaLibFeedback = (() => {
  const states = new WeakMap();
  const labels = new WeakMap();
  const messages = {
    400: "Einige Angaben sind ungültig. Bitte überprüfe deine Eingaben.",
    403: "Die Anfrage wurde aus Sicherheitsgründen abgewiesen. Bitte lade die Seite neu, bevor du es erneut versuchst.",
    404: "Der gewünschte Eintrag wurde nicht gefunden.",
    500: "Beim Verarbeiten ist ein Fehler aufgetreten. Bitte versuche es später erneut.",
    unknown: "Die Aktion konnte nicht eindeutig bestätigt werden. Bitte überprüfe den gespeicherten Stand, bevor du sie erneut ausführst."
  };
  function show(container, message) {
    let label = labels.get(container);
    if (!label) {
      label = document.createElement("p");
      label.className = "write-feedback";
      label.setAttribute("role", "alert");
      label.setAttribute("aria-live", "polite");
      label.tabIndex = -1;
      const target = container.matches("main") ? container
        : container.querySelector(".popup, .popup-edit, .popup-edit-band, .ask-panel") || container;
      target.prepend(label);
      labels.set(container, label);
    }
    label.textContent = message;
    label.hidden = !message;
    if (message) label.focus();
  }
  function clear(container) {
    const button = container.matches("main") ? null : container.querySelector(".save-button, .save-button-edit");
    const state = button && states.get(button);
    // Abbrechen/erneut Öffnen hebt eine unbestätigte Speicherung nicht auf.
    show(container, state && state !== "pending" ? state : "");
  }
  function error(status) { const e = new Error("Request rejected"); e.status = status; return e; }
  async function read(response, { empty = false, allow417 = false, validate } = {}) {
    if (!response.ok && !(allow417 && response.status === 417)) throw error(response.status);
    if (response.status === empty) return null; // Bekannte leere Verträge: Band-204 und Delete-200.
    const type = response.headers.get("Content-Type") || "";
    if (!/^application\/(?:json|[\w.+-]+\+json)(?:\s*;|$)/i.test(type)) throw error();
    let data;
    try { data = await response.json(); } catch { throw error(); }
    if (!data || typeof data !== "object" || Array.isArray(data)) throw error();
    if (response.status === 417 && data.message !== "Bitte Status für neue Reihe auswählen") throw error();
    if (validate && !validate(data)) throw error();
    return data;
  }
  async function request(path, options, container, { button, persist = true, ...contract } = {}) {
    button ||= container.querySelector(".save-button, .save-button-edit");
    if (persist && button && states.has(button)) {
      show(container, messages.unknown);
      throw error();
    }
    clear(container);
    if (persist && button) { states.set(button, "pending"); button.disabled = true; }
    try {
      const response = await mangaLibFetch(path, options);
      const data = await read(response, contract);
      if (persist && button && response.status === 417) { states.delete(button); button.disabled = false; }
      return data;
    } catch (e) {
      const status = e?.status;
      const message = messages[status] || (status >= 500 ? messages[500] : messages.unknown);
      show(container, message);
      // 5xx/Transport/Formatfehler können nach einer Speicherung auftreten.
      // Bis zum Neuladen gesperrt halten; niemals automatisch erneut schreiben.
      if (persist && button && [400, 404, 405, 415, 422].includes(status)) {
        states.delete(button); button.disabled = false;
      } else if (persist && button) {
        states.set(button, message);
      }
      throw e;
    }
  }
  return { show, clear, read, request };
})();

function mangaLibCoverPlaceholder() {
  return document.body.dataset.coverPlaceholder === "/manual-test/cover.svg"
    ? "/manual-test/cover.svg" : "/svg/filler_img2.png";
}
function mangaLibCoverUrl(value) {
  const fallback = mangaLibCoverPlaceholder();
  if (!value) return fallback;
  // Die manuelle CSP erlaubt nur lokale Bilder. Gespeicherte URLs nicht umschreiben.
  if (fallback === "/manual-test/cover.svg") {
    try { if (new URL(value, location.href).origin !== location.origin) return fallback; }
    catch { return fallback; }
  }
  return value;
}
function mangaLibSetCover(image, value) {
  image.onerror = () => { image.onerror = null; image.src = mangaLibCoverPlaceholder(); };
  image.src = mangaLibCoverUrl(value);
}
