/* Session-CSRF aus der gerenderten Seite; keine Token-URLs, fremden Ziele oder Schreib-Replays. */
async function mangaLibFetch(path, options = {}) {
  const fail = (message) => {
    alert(message);
    throw new Error(message);
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
