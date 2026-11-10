(() => {
  const fields = [
    ["titel", "Titel", "text"],
    ["mangaIndex", "Manga Index", "number"],
    ["verlagId", "Verlag", "select", "verlag"],
    ["typId", "Typ", "select", "typ"],
    ["formatId", "Format", "select", "format"],
    ["erscheinungsdatum", "Erscheinungsdatum", "date"],
    ["sammelbandTypId", "Sammelbandtyp", "select", "sammelband_typ"],
    ["anzahlBaende", "Anzahl Bände", "number"],
    ["preisProBand", "Preis pro Band in €", "money"],
    ["gesamtpreisAenderung", "Änderung Preis in €", "money"],
    ["istEbayPreis", "eBay-Preis", "flag"],
    ["istVergriffen", "Vergriffen", "flag"]
  ];

  document.querySelectorAll(".shopping-edit-button").forEach(button => {
    button.addEventListener("click", async () => {
      if (document.querySelector(".shopping-editor")) return;
      button.disabled = true;
      try {
        const path = "/items/" + encodeURIComponent(button.dataset.id);
        const state = await MangaLibFeedback.request(path + "/edit", {}, document.querySelector("main"),
          { persist: false, validate: value => typeof value.editToken === "string" });
        if (!state.editable) {
          MangaLibFeedback.show(document.querySelector("main"), "Der Eintrag ist schreibgeschützt.");
          return;
        }
        if (!document.querySelector(".shopping-editor")) openEditor(path, state, button);
      } catch {
        // Der gemeinsame Helfer zeigt den Ladefehler an.
      } finally {
        if (!document.querySelector(".shopping-editor")) button.disabled = false;
      }
    });
  });

  function openEditor(path, state, opener) {
    const overlay = document.createElement("div");
    overlay.className = "popup-container shopping-editor";
    overlay.style.display = "flex";
    const panel = document.createElement("div");
    panel.className = "popup";
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-modal", "true");
    panel.setAttribute("aria-label", "Einkaufsposition bearbeiten");
    const form = document.createElement("form");
    const title = document.createElement("h2");
    title.textContent = "Einkaufsposition bearbeiten";
    form.append(title);
    const controls = new Map();
    for (const [key, label, type, source] of fields) {
      const group = document.createElement("label");
      group.className = "input-group";
      group.append(label);
      const input = document.createElement(type === "select" || type === "flag" ? "select" : "input");
      if (source) {
        const options = document.getElementById(source);
        if (options) for (const option of options.options) input.append(option.cloneNode(true));
      } else if (type === "flag") {
        input.append(new Option("Unbekannt", ""), new Option("Ja", "true"), new Option("Nein", "false"));
      } else {
        input.type = type === "money" ? "text" : type;
        if (type === "money") input.inputMode = "decimal";
        input.placeholder = state[key] == null ? "Unbekannt" : "";
      }
      input.value = state[key] == null ? "" : String(state[key]);
      controls.set(key, { input, type, initial: input.value });
      group.append(input);
      form.append(group);
    }
    const summary = document.createElement("p");
    summary.textContent = state.gesamtpreis == null ? "Gesamtpreis: unbekannt"
      : "Gespeicherter Gesamtpreis: " + state.gesamtpreis.replace(".", ",") + " €";
    const preview = document.createElement("p");
    preview.setAttribute("aria-live", "polite");
    const cents = value => {
      const match = /^([+-]?)(\d+)(?:[,.](\d{1,2}))?$/.exec(value.trim());
      return match ? (BigInt(match[2]) * 100n + BigInt((match[3] || "").padEnd(2, "0")))
        * (match[1] === "-" ? -1n : 1n) : null;
    };
    form.addEventListener("input", () => {
      const count = controls.get("anzahlBaende").input.value;
      const price = cents(controls.get("preisProBand").input.value);
      const adjustment = cents(controls.get("gesamtpreisAenderung").input.value);
      if (!/^\d{1,5}$/.test(count) || Number(count) < 1 || Number(count) > 10000
          || price === null || adjustment === null) {
        preview.textContent = "Preisvorschau: unbekannt";
        return;
      }
      const total = BigInt(count) * price + adjustment;
      const absolute = total < 0n ? -total : total;
      preview.textContent = "Preisvorschau: " + (total < 0n ? "−" : "") + (absolute / 100n)
        + "," + String(absolute % 100n).padStart(2, "0") + " €";
    });
    const save = document.createElement("button");
    save.className = "save-button";
    save.textContent = "Speichern";
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "cancel-button";
    cancel.textContent = "Abbrechen";
    form.append(summary, preview, save, cancel);
    panel.append(form);
    overlay.append(panel);
    document.body.append(overlay);
    controls.get("titel").input.focus();
    cancel.addEventListener("click", () => {
      overlay.remove();
      opener.disabled = save.disabled;
      if (!opener.disabled) opener.focus();
    });
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (save.disabled) return;
      const body = { editToken: state.editToken };
      for (const [key, { input, initial, type }] of controls) {
        if (input.value === initial) continue;
        const value = input.value.trim();
        body[key] = type === "number" ? (value ? Number(value) : null)
          : type === "flag" ? (value ? value === "true" : null)
          : type === "money" ? value.replace(",", ".") : value || null;
      }
      cancel.disabled = true;
      try {
        await MangaLibFeedback.request(path, {
          method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
        }, form, { button: save, validate: result => result.id === state.id && typeof result.editToken === "string" });
        window.location.reload();
      } catch {
        // Bei ungewissem Ergebnis bleibt der Save bis zum Reload gesperrt.
        cancel.disabled = false;
      }
    });
  }
})();
