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
  const purchaseConflicts = Object.freeze({
    UNKNOWN_STATUS: "Der gespeicherte Kaufstatus ist unbekannt. Bitte kläre den Eintrag vor der Übernahme.",
    AMBIGUOUS_SERIES: "Die Zielreihe ist nicht eindeutig. Es wurde nichts übernommen.",
    INCOMPATIBLE_SERIES: "Die gefundene Ausgabe passt nicht zur Einkaufsposition. Es wurde nichts übernommen.",
    BAND_CONFLICT: "Der bestätigte Bandbereich passt nicht mehr zum Bestand. Bitte lade die Seite neu.",
    PURCHASE_STATE_CONFLICT: "Der Einkauf wurde inzwischen geändert. Bitte lade die Seite neu und bestätige erneut.",
    SHOPPING_EDIT_CONFLICT: "Der Eintrag wurde geändert oder ist gesperrt. Bitte lade die Seite neu.",
    INVALID_INVENTORY: "Der vorhandene Bestand ist unvollständig oder widersprüchlich. Es wurde nichts übernommen.",
    BUSY: "Eine andere Schreiboperation ist noch aktiv. Bitte versuche es später bewusst erneut."
  });
  const messages = {
    400: "Einige Angaben sind ungültig. Bitte überprüfe deine Eingaben.",
    403: "Die Anfrage wurde aus Sicherheitsgründen abgewiesen. Bitte lade die Seite neu, bevor du es erneut versuchst.",
    404: "Der gewünschte Eintrag wurde nicht gefunden.",
    413: "Die Anfrage überschreitet die technischen Größenbegrenzungen. Bitte reduziere die Importdaten und lade die Seite vor einem erneuten Versuch neu.",
    415: "Das Anfrageformat wird nicht unterstützt. Bitte überprüfe deine Eingaben.",
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
    if (!response.ok && !(allow417 && response.status === 417)) {
      const rejected = error(response.status);
      if (response.status === 409) {
        const code = response.headers.get("X-MangaLib-Purchase-Outcome");
        if (Object.hasOwn(purchaseConflicts, code)) rejected.purchaseOutcome = code;
      }
      throw rejected;
    }
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
      if (persist && button && (response.status === 417
          || contract.allowConfirmation && data.message === "Bitte Bände bestätigen")) {
        states.delete(button); button.disabled = false;
      }
      return data;
    } catch (e) {
      const status = e?.status;
      const purchaseMessage = e?.purchaseOutcome && purchaseConflicts[e.purchaseOutcome];
      const message = purchaseMessage || messages[status] || (status >= 500 ? messages[500] : messages.unknown);
      show(container, message);
      // 5xx/Transport/Formatfehler können nach einer Speicherung auftreten.
      // Bis zum Neuladen gesperrt halten; niemals automatisch erneut schreiben.
      if (persist && button && ([400, 404, 405, 415, 422].includes(status) || status === 409 && purchaseMessage)) {
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
  if (typeof value !== "string" || !value) return fallback;
  // Nur diese eigenen statischen Platzhalter dürfen relative Pfade sein.
  if (value === "/svg/filler_img2.png" || value === "/manual-test/cover.svg") return value;
  const text = value.trim();
  if (!/^https?:\/\//i.test(text) || /[\u0000-\u0020\u007f\\]/.test(text)) return fallback;
  let url;
  try {
    url = new URL(text);
    if (!["http:", "https:"].includes(url.protocol) || !url.hostname || url.username || url.password) return fallback;
  } catch { return fallback; }
  // Die manuelle CSP erlaubt nur lokale Bilder. Gespeicherte URLs nicht umschreiben.
  if (fallback === "/manual-test/cover.svg") {
    if (url.origin !== location.origin) return fallback;
  }
  return url.href;
}
function mangaLibSetCover(image, value) {
  image.onerror = () => { image.onerror = null; image.src = mangaLibCoverPlaceholder(); };
  image.src = mangaLibCoverUrl(value);
}
