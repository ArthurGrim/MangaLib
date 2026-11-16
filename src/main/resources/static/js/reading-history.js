/* Leseaktionen sind vom Metadatenspeichern getrennt. Keine automatischen Schreib-Replays. */
(() => {
  let capability;
  let revision = 0;
  const uncertainBands = new Set();
  async function enabled() {
    capability ||= mangaLibFetch("/reading-history/status").then(response => {
      if (!response.ok) throw new Error("Unavailable");
      return response.json();
    });
    return capability;
  }
  function text(tag, value) {
    const element = document.createElement(tag);
    element.textContent = value;
    return element;
  }
  function validEvent(event) {
    return event && Number.isSafeInteger(event.id) && event.id > 0
      && ["FIRST_READ", "REREAD"].includes(event.kind) && /^\d{4}-\d{2}-\d{2}$/.test(event.date)
      && Number.isSafeInteger(event.revision) && event.revision >= 0;
  }
  window.mangaLibLoadReadingHistory = async bandId => {
    const section = document.getElementById("band-reading-history");
    if (!section) return;
    const current = ++revision;
    section.hidden = true;
    section.replaceChildren();
    try {
      const state = await enabled();
      if (current !== revision || !state.enabled) return;
      document.getElementById("reread_band").readOnly = true;
      const response = await mangaLibFetch(`/reading-history/bands/${encodeURIComponent(bandId)}`);
      if (!response.ok) throw new Error("Unavailable");
      const data = await response.json();
      if (current !== revision) return;
      if (!Array.isArray(data.events) || !data.events.every(validEvent)) throw new Error("Invalid history");
      section.hidden = false;
      if (uncertainBands.has(String(bandId))) {
        section.append(text("p", "Eine Leseaktion wurde noch nicht eindeutig bestätigt. Bitte Seite neu laden und den gespeicherten Stand prüfen."));
        return;
      }
      section.append(text("h3", "Lesehistorie"), text("p", "Neue Lektüre ausdrücklich abschließen. Datumskorrekturen ändern den Reread-Zähler nicht. Zurücksetzen auf ungelesen erhält die Historie."));
      const dateLabel = text("label", "Abschlussdatum");
      const date = document.createElement("input");
      date.type = "date"; date.required = true; date.min = "1000-01-01";
      date.max = state.today; date.value = state.today; dateLabel.append(date);
      section.append(dateLabel);
      let pending = false;
      const write = async (button, path, body) => {
        if (pending) return;
        pending = true;
        uncertainBands.add(String(bandId));
        try {
          await MangaLibFeedback.request(path, { method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body) }, section, { button, validate: data => !!data.event || Array.isArray(data.events) });
          window.location.reload();
        } catch (error) {
          // Fachliche Konflikte bekommen einen festen Text, niemals den Server-Fehlerbody.
          // Auch hier bleibt die Aktion bis zum bewussten Reload gesperrt.
          if (error?.status === 409) MangaLibFeedback.show(section,
            "Die Leseaktion widerspricht der gespeicherten Historie. Bitte prüfe die Datumsreihenfolge und lade die Seite neu.");
        }
      };
      for (const [kind, label] of [["FIRST_READ", "Erstlektüre dokumentieren"], ["REREAD", "Erneut gelesen"]]) {
        const button = text("button", label); button.type = "button";
        button.disabled = kind === "FIRST_READ" && data.events.some(event => event.kind === kind);
        button.addEventListener("click", () => {
          if (!date.reportValidity()) return;
          write(button, "/reading-history/complete", { bandIds: [String(bandId)], operationId: crypto.randomUUID(), kind, date: date.value });
        });
        section.append(button);
      }
      const list = document.createElement("ul");
      for (const event of data.events) {
        const row = document.createElement("li");
        const label = text("label", event.kind === "FIRST_READ" ? "Erstlektüre " : "Reread ");
        const corrected = document.createElement("input"); corrected.type = "date";
        corrected.required = true; corrected.min = "1000-01-01"; corrected.max = state.today; corrected.value = event.date;
        label.append(corrected); row.append(label);
        const button = text("button", "Datum korrigieren"); button.type = "button";
        button.addEventListener("click", () => {
          if (!corrected.reportValidity()) return;
          write(button, `/reading-history/bands/${encodeURIComponent(bandId)}/events/${event.id}/date`,
            { revision: event.revision, date: corrected.value });
        });
        row.append(button); list.append(row);
      }
      section.append(list);
    } catch {
      if (current === revision) {
        section.hidden = false;
        section.replaceChildren(text("p", "Lesehistorie konnte nicht geladen werden. Bitte neu laden."));
      }
    }
  };

  document.addEventListener("DOMContentLoaded", async () => {
    try {
      const state = await enabled();
      if (!state.enabled) return;
      for (const id of ["reread-edit", "reread_band", "reread"]) {
        const input = document.getElementById(id); if (input) input.readOnly = true;
      }
      const apply = document.getElementById("apply-reread-edit"); if (apply) apply.disabled = true;
      const readOption = document.querySelector('label[for="apply-read-edit"]');
      if (readOption) readOption.textContent = `Lesestatus auf alle vorhandenen Bände anwenden (neue Erstlektüre: ${state.today})`;
      const monthly = document.getElementById("reading-history-months");
      if (!monthly) return;
      monthly.hidden = false;
      const response = await mangaLibFetch("/reading-history/months");
      if (!response.ok) throw new Error("Unavailable");
      const data = await response.json();
      if (!Array.isArray(data.months)) throw new Error("Invalid history");
      const table = document.createElement("table");
      const header = document.createElement("tr");
      for (const label of ["Monat", "Erstlektüren", "Rereads", "Geschätzte Minuten", "Unbekannter Faktor"]) header.append(text("th", label));
      table.append(header);
      for (const month of data.months) {
        const row = document.createElement("tr");
        for (const value of [month.month, month.firstReads, month.rereads, month.minutes, month.unknownFactors]) row.append(text("td", value));
        table.append(row);
      }
      monthly.append(table, text("p", "Nur datierte Abschlüsse. Frühere unbekannte Lesedaten werden nicht geschätzt. Jede Lektüre zählt mit 45 Minuten × dem beim Abschluss gespeicherten Sammelbandfaktor."));
    } catch {
      const monthly = document.getElementById("reading-history-months");
      if (monthly) { monthly.hidden = false; monthly.append(text("p", "Lesehistorie konnte nicht geladen werden.")); }
    }
  });
})();
