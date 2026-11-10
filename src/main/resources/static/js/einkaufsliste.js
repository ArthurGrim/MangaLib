document.addEventListener("DOMContentLoaded", function () {
  let scrapedData = {};

  const deleteButtons = document.querySelectorAll(".delete-button");

  deleteButtons.forEach((button) => {
    button.addEventListener("click", function (event) {
      // Bestätigungsdialog anzeigen
      const confirmed = confirm(
        "Sind Sie sicher, dass Sie diesen Eintrag löschen möchten?"
      );
      if (!confirmed) {
        // Wenn der Benutzer nicht bestätigt, Abbruch des Löschvorgangs
        return;
      }
      // Auslesen der data-id vom Bild-Element innerhalb des Buttons
      const itemId = event.currentTarget.getAttribute("data-id");
      console.log("Deleting item with id:", itemId);

      MangaLibFeedback.request(`/items/${itemId}`, { method: "DELETE" },
        document.querySelector("main"), { button, empty: 200 })
        .then(() => window.location.reload())
        .catch(() => {});

    });
  });

  // Event-Listener für den Add-Button
  var addButtonHeader = document.getElementById("add-button-header");
  if (addButtonHeader) {
    addButtonHeader.addEventListener("click", function () {
      // Popup anzeigen
      var popup = document.getElementById("popupContainer");
      if (popup) {
        popup.style.display = "flex";
      }
    });
  }

  // Event-Listener für alle Add-Buttons
  var addButtons = document.querySelectorAll(".add-button");

  addButtons.forEach(function (button) {
    button.addEventListener("click", function (event) {
      // Zugriff auf den Container des jeweiligen Buttons
      var container = event.target.closest(".monats-container");

      // Ermitteln des Monatsnamens
      var monthName = container.querySelector(".monats-name").textContent;
      var monthNumber = convertMonthNameToNumber(monthName);

      // Setzen des Monats im Pop-up
      document.querySelector(".monat-select").value = monthNumber;

      // Popup anzeigen
      var popup = document.getElementById("popupContainer");
      if (popup) {
        popup.style.display = "flex";
      }
    });
  });

  const cancelButton = document.querySelector(".cancel-button");
  // Event-Handler, um das Pop-up zu verbergen
  cancelButton.addEventListener("click", function () {
    popupContainer.style.display = "none";
    resetPopupFields();
  });

  function resetPopupFields() {
    // Zurücksetzen aller Eingabefelder
    document
      .querySelectorAll(
        '.popup input[type="text"], .popup input[type="number"]'
      )
      .forEach((input) => {
        input.value = "";
      });

    // Zurücksetzen aller Select-Elemente
    document.querySelectorAll(".popup select").forEach((select) => {
      select.selectedIndex = 0;
    });

    // Zurücksetzen der Checkboxen
    document
      .querySelectorAll('.popup input[type="checkbox"]')
      .forEach((checkbox) => {
        checkbox.checked = false;
      });

    // Aktualisieren der Sichtbarkeit basierend auf Checkboxen
    updateVisibility();
  }

  // Referenzen auf Checkboxen, Felder und Labels
  const istEbayPreisCheckbox = document.getElementById("istebaypreis");
  const gesamtpreisAenderungFeld = document.getElementById(
    "gesamtpreis_aenderung"
  );
  const gesamtpreisAenderungLabel = document.querySelector(
    "label[for='gesamtpreis_aenderung']"
  );
  const istSammelbandCheckbox = document.getElementById("istsammelband");
  const sammelbandTypFeld = document.getElementById("sammelband_typ");
  const sammelbandTypLabel = document.querySelector(
    "label[for='sammelband_typ']"
  );

  // Funktion zum Aktualisieren der Sichtbarkeit
  function updateVisibility() {
    gesamtpreisAenderungFeld.style.display = istEbayPreisCheckbox.checked
      ? "block"
      : "none";
    gesamtpreisAenderungLabel.style.display = istEbayPreisCheckbox.checked
      ? "block"
      : "none";

    sammelbandTypFeld.style.display = istSammelbandCheckbox.checked
      ? "block"
      : "none";
    sammelbandTypLabel.style.display = istSammelbandCheckbox.checked
      ? "block"
      : "none";
  }

  // Event-Handler für Checkboxen
  istEbayPreisCheckbox.addEventListener("change", updateVisibility);
  istSammelbandCheckbox.addEventListener("change", updateVisibility);

  // Initialer Aufruf, um die anfängliche Sichtbarkeit zu setzen
  updateVisibility();

  function convertMonthNameToNumber(monthName) {
    const monthNames = [
      "Januar",
      "Februar",
      "März",
      "April",
      "Mai",
      "Juni",
      "Juli",
      "August",
      "September",
      "Oktober",
      "November",
      "Dezember",
    ];
    return monthNames.indexOf(monthName) + 1;
  }

  function isNum(value) {
    var reg = /^[0-9]\d*$/;
    return reg.test(value);
  }

  const saveButton = document.querySelector(".save-button");

  // Event-Handler für den Speichern-Button
  saveButton.addEventListener("click", function (event) {
    event.preventDefault(); // Verhindert das Standardverhalten des Formulars
    MangaLibFeedback.clear(document.getElementById("popupContainer"));
    console.log("Save-Button geklickt");

    // Eingabefelder und Selects auslesen
    const mangaIndex = document.querySelector("#manga_index").value.trim();
    const verlagId = document.querySelector("#verlag").value;
    const typId = document.querySelector("#typ").value;
    const formatId = document.querySelector("#format").value;
    const titel = document.querySelector("#titel").value.trim();
    const anzahlBaende = document.querySelector("#anzahl_baende").value.trim();
    const preisProBandString = document
      .querySelector("#preis_pro_band")
      .value.trim();
    const preisProBand = preisProBandString.replace(",", ".");
    const anilistUrl = document.querySelector("#anilist_url").value.trim();
    const istVergriffen = document.querySelector("#istvergriffen").checked;
    const istEbayPreis = document.querySelector("#istebaypreis").checked;
    const gesamtpreisAenderungString = document
      .querySelector("#gesamtpreis_aenderung")
      .value.trim();
    const gesamtpreisAenderung = gesamtpreisAenderungString.replace(",", ".");
    const istSammelband = document.querySelector("#istsammelband").checked;
    const sammelbandTypId = document.querySelector("#sammelband_typ").value;
    const tag = document.querySelector(".tag-select").value;
    const monat = document.querySelector(".monat-select").value;
    const jahr = document.querySelector(".jahr-select").value;

    // Validierung der Werte
    if (mangaIndex && !isNum(mangaIndex)) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "MangaIndex muss eine positive ganze Zahl sein.");
      return;
    }
    if (!verlagId) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Bitte wählen Sie einen Verlag aus.");
      return;
    }
    if (!typId) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Bitte wählen Sie einen Typ aus.");
      return;
    }
    if (!formatId) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Bitte wählen Sie ein Format aus.");
      return;
    }
    if (!titel) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Bitte geben Sie einen Titel ein.");
      return;
    }
    if (!isNum(anzahlBaende)) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Anzahl der Bände muss eine positive ganze Zahl sein.");
      return;
    }
    if (!/^\d+(\.\d{1,2})?$/.test(preisProBand)) {
      MangaLibFeedback.show(document.getElementById("popupContainer"),
        "Preis pro Band muss eine Zahl mit maximal zwei Dezimalstellen sein."
      );
      return;
    }
    if (istEbayPreis && !/^[-+]?\d+(\.\d+)?$/.test(gesamtpreisAenderung)) {
      MangaLibFeedback.show(document.getElementById("popupContainer"),
        "Der eBay-Preis muss eine gültige Dezimalzahl sein (Vorzeichen relevant)."
      );
      return;
    }

    if (anilistUrl && !anilistUrl.match(/^https?:\/\/.+$/)) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Bitte geben Sie eine gültige AniList-URL ein.");
      return;
    }

    if (!tag || !monat || !jahr) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Bitte geben Sie ein gültiges Erscheinungsdatum ein.");
      return;
    }

    console.log("Ausgelesene Werte:");
    console.log("MangaIndex: ", mangaIndex);
    console.log("VerlagID: ", verlagId);
    console.log("TypId: ", typId);
    console.log("FormatId: ", formatId);
    console.log("Titel: ", titel);
    console.log("Anzahl Baende: ", anzahlBaende);
    console.log("preisProBand: ", preisProBand);
    console.log("anilistUrl: ", anilistUrl);
    console.log("istVergriffen: ", istVergriffen);
    console.log("istEbayPreis: ", istEbayPreis);
    console.log("gesamtpreisAenderung: ", gesamtpreisAenderung);
    console.log("istSammelband: ", istSammelband);
    console.log("sammelbandTypId: ", sammelbandTypId);
    console.log("Tag, Monat, Jahr:", tag + " " + monat + " " + jahr);

    scrapedData["istEdit"] = "false";

    // Konstruktion des Datums im richtigen Format (z.B. YYYY-MM-DD)
    const erscheinungsdatum =
      jahr && monat && tag
        ? `${jahr}-${monat.padStart(2, "0")}-${tag.padStart(2, "0")}`
        : null;

    // Objekt erstellen, das gesendet werden soll
    const mangaReiheData = {
      mangaIndex: mangaIndex ? parseInt(mangaIndex, 10) : null,
      verlagId,
      typId,
      formatId,
      titel,
      anzahlBaende: parseInt(anzahlBaende, 10),
      preisProBand,
      istVergriffen,
      istEbayPreis,
      gesamtpreisAenderung: istEbayPreis ? gesamtpreisAenderung : "0",
      anilistUrl,
      sammelbandTypId: istSammelband ? sammelbandTypId : null,
      erscheinungsdatum: erscheinungsdatum,
      scrapedData: scrapedData,
    };

    // Loggen der zu sendenden Daten
    console.log("Zu sendende Daten:", mangaReiheData);

    // Daten senden mit Fetch-API
    MangaLibFeedback.request("/addToEinkaufsliste", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(mangaReiheData),
    }, document.getElementById("popupContainer"))
      .then((data) => {
        console.log("Antwort vom Server:", data); // Erfolgsmeldung oder Datenverarbeitung
        // Schließen des Popups
        document.querySelector("#popupContainer").style.display = "none";
        resetPopupFields();
        window.location.reload();
      })
      .catch(() => {}); // Die gemeinsame Hilfe zeigt die sichere Dialogmeldung.
  });

  const autofillButton = document.querySelector(".mp-autofill-button");
  const mangaIndexInput = document.querySelector("#manga_index");

  function showLoading() {
    document.getElementById("popupContainer").querySelector(".loading-label").style.display = "block";
  }

  function hideLoading() {
    document.getElementById("popupContainer").querySelector(".loading-label").style.display = "none";
  }

  function showError() {
    const errorLabel = document.getElementById("popupContainer").querySelector(".error-label");
    errorLabel.style.display = "block";

    setTimeout(() => {
      errorLabel.style.display = "none";
    }, 2000); // Fehler verschwindet nach 2 Sekunden
  }

  // Event-Handler, für den MangaPassion-Autofill Button
  autofillButton.addEventListener("click", function () {
    const mangaIndex = mangaIndexInput.value.trim();

    // Validierung des Manga-Index
    if (!mangaIndex || isNaN(mangaIndex)) {
      MangaLibFeedback.show(document.getElementById("popupContainer"), "Bitte geben Sie einen gültigen Manga-Index ein.");
      return;
    }

    MangaLibFeedback.clear(document.getElementById("popupContainer"));
    // Anzeigen des Lade-Labels
    showLoading();

    // Senden des Manga-Index an den Web Scraper
    MangaLibFeedback.request("/scrape", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ mangaIndex: mangaIndex }),
    }, document.getElementById("popupContainer"), { persist: false })
      .then((data) => {
        // Daten in die Eingabefelder und Selects einfügen
        document.querySelector("#verlag").value = data["verlagId"];
        document.querySelector("#typ").value = data["typId"];
        document.querySelector("#titel").value = data["Titel"];
        document.querySelector("#format").value = data["formatId"];
        // Anzahl Bände: Serien liefern "Deutsche Ausgabe Bände", Einzelband nicht -> fallback 1
        const baendeRaw = data["Deutsche Ausgabe Bände"];
        const baende =
          baendeRaw != null && String(baendeRaw).trim() !== ""
            ? String(baendeRaw).replace("+", "").trim()
            : "1";

        document.querySelector("#anzahl_baende").value = baende;
        // ==========================
        // Preis pro Band ermitteln
        // ==========================

        // akzeptiert "7,00", "7.00", "€ 7,00", "´┐¢ 7,00"
        const extractPrice = (raw) => {
          if (!raw) return null;
          const m = String(raw).match(/(\d{1,3}(?:[.,]\d{2})?)/);
          return m ? m[1].replace(".", ",") : null;
        };

        // 1️⃣ Letzten Bandpreis suchen
        let lastBandPrice = null;

        for (let i = 1; ; i++) {
          const key = `Band ${i} Preis`;
          if (!(key in data)) break;

          const p = extractPrice(data[key]);
          if (p && p !== "0,00") {
            lastBandPrice = p;
          }
        }

        // 2️⃣ Fallback: Deutsche Ausgabe Preis (Einzelband / Sammelband)
        let germanEditionPrice = extractPrice(data["Deutsche Ausgabe Preis"]);

        // 3️⃣ Entscheidung
        let finalPrice = "0,00";

        if (lastBandPrice) {
          finalPrice = lastBandPrice;
        } else if (germanEditionPrice && germanEditionPrice !== "0,00") {
          finalPrice = germanEditionPrice;
        }

        // setzen
        document.querySelector("#preis_pro_band").value = finalPrice;

        // Verstecken des Lade-Labels
        hideLoading();

        // Entfernen der nicht mehr benötigten Schlüssel
        delete data["verlagId"];
        delete data["typId"];
        delete data["formatId"];

        // Speichern der gesamten Map für den Save-Button
        Object.keys(data).forEach((key) => {
          scrapedData[key] = data[key];
        });
      })
      .catch((error) => {
        hideLoading();
        showError();
        // Keine Rohfehler oder Antwortbodys anzeigen.
      });
  });

  const buyButtons = document.querySelectorAll(".buy-button");
  let purchaseInProgress = false;

  // Aktionen beim Klicken des Kauf-Buttons
  buyButtons.forEach((button) => {
    button.addEventListener("click", function () {
      // Der gemeinsame Statusdialog darf nicht gleichzeitig zwei Positionen zugeordnet sein.
      if (purchaseInProgress) return;
      purchaseInProgress = true;
      const itemId = this.getAttribute("data-id");
      const feedback = document.querySelector("main");
      MangaLibFeedback.request("/buyItemUpdate", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ itemId: itemId })
      }, feedback, { button, allow417: true, allowConfirmation: true, validate: validPurchaseResponse })
        .then(async data => {
          if (data.message === "Kauf bereits übernommen") {
            MangaLibFeedback.show(feedback, "Kauf bereits übernommen. Es wurde nichts erneut hinzugefügt.");
            button.disabled = true;
            return;
          }
          if (data.message === "Bitte Bände bestätigen") {
            button.disabled = true;
            const completed = await askForVolumeRange(itemId, null, data, button);
            if (completed) window.location.reload();
            return;
          }
          if (["Reihe aktualisiert", "Neue MangaReihe erfolgreich gespeichert"].includes(data.message)) {
            button.disabled = true;
            window.location.reload();
            return;
          }
          button.disabled = true;
          const completed = await askUserForNewMangaReiheStatus(itemId, button, data);
          if (completed) window.location.reload();
        })
        .catch(() => {})
        .finally(() => { purchaseInProgress = false; });

    });
  });

  //PopUp wenn es sich um eine neue Reihe handelt und der User den Status eingeben soll.
  function purchaseDescription(preview) {
    const summary = preview.purchaseSummary;
    if (!summary) return "";
    const amount = value => value == null ? "unbekannt" : String(value).replace(".", ",") + " €";
    return `${summary.titel} · ${summary.anzahlBaende} Bände · je ${amount(summary.preisProBand)} · Anpassung ${amount(summary.gesamtpreisAenderung)} · gesamt ${amount(summary.gesamtpreis)}`;
  }

  function askUserForNewMangaReiheStatus(itemId, buyButton, preview) {
    return new Promise((resolve) => {
      console.log("PopUp zur Auswahl des Status anzeigen.");
      var statusPopup = document.querySelector(".ask-for-status-popup");
      statusPopup.style.visibility = "visible";
      MangaLibFeedback.clear(statusPopup);
      let attempted = false;

      var confirmButton = document.getElementById("confirmStatus");
      var cancelButton = document.getElementById("cancelStatus");
      const summary = document.createElement("p");
      summary.textContent = purchaseDescription(preview);
      confirmButton.before(summary);

      // Hilfsfunktion, um Event Listener zu entfernen
      function cleanUp() {
        summary.remove();
        confirmButton.removeEventListener("click", onConfirm);
        cancelButton.removeEventListener("click", onCancel);
        statusPopup.style.visibility = "hidden";

        // Auswahl im Select zurücksetzen
        var statusSelect = document.getElementById("status");
        statusSelect.value = "";
      }

      // Event Handler für den Confirm-Button
      async function onConfirm() {
        var selectedStatus = document.getElementById("status").value;
        if (selectedStatus) {
          attempted = true;
          try {
            const data = await MangaLibFeedback.request("/buyItemCreate", {
              method: "POST", headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ itemId: itemId, statusId: selectedStatus, purchaseToken: preview.purchaseToken })
            }, statusPopup, { button: confirmButton, allowConfirmation: true,
              validate: data => data.message !== "Bitte Status für neue Reihe auswählen" && validPurchaseResponse(data) });
            cleanUp();
            if (data.message === "Kauf bereits übernommen") {
              MangaLibFeedback.show(document.querySelector("main"), "Kauf bereits übernommen. Es wurde nichts erneut hinzugefügt.");
              resolve(false);
              return;
            }
            if (data.message === "Bitte Bände bestätigen") {
              resolve(await askForVolumeRange(itemId, selectedStatus, data, buyButton));
              return;
            }
            resolve(true);
          } catch { /* Fehlermeldung, Werte und Dialog bleiben erhalten. */ }
        } else {
          MangaLibFeedback.show(statusPopup, "Bitte wählen Sie einen Status aus.");
        }
      }

      // Event Handler für den Cancel-Button
      function onCancel() {
        cleanUp();
        if (!attempted) buyButton.disabled = false;
        resolve(false);
      }

      confirmButton.addEventListener("click", onConfirm);
      cancelButton.addEventListener("click", onCancel);
    });
  }

  function validPurchaseResponse(data) {
    if (data.message === "Bitte Bände bestätigen") {
      return typeof data.seriesId === "string" && /^[1-9][0-9]{0,18}$/.test(data.seriesId)
        && Number.isInteger(data.firstBand) && Number.isInteger(data.lastBand)
        && data.firstBand > 0 && data.lastBand >= data.firstBand && data.lastBand <= 10000;
    }
    return ["Reihe aktualisiert", "Neue MangaReihe erfolgreich gespeichert", "Kauf bereits übernommen",
      "Bitte Status für neue Reihe auswählen"].includes(data.message);
  }

  // Ein bewusster zweiter Benutzerauftrag, kein automatischer Retry des ersten Requests.
  function askForVolumeRange(itemId, statusId, preview, buyButton) {
    return new Promise(resolve => {
      const popup = document.createElement("div");
      popup.className = "ask-for-status-popup purchase-range-popup";
      popup.style.visibility = "visible";
      const panel = document.createElement("div"); panel.className = "ask-panel";
      panel.setAttribute("role", "dialog"); panel.setAttribute("aria-label", "Kaufbände bestätigen");
      const text = document.createElement("p");
      text.textContent = `Bände ${preview.firstBand}–${preview.lastBand} in die vorhandene Reihe übernehmen? Bereits vorhandene Bände bleiben erhalten.`;
      const summary = document.createElement("p");
      summary.textContent = purchaseDescription(preview);
      const confirm = document.createElement("button"); confirm.type = "button";
      confirm.className = "ask-panel-confirm-button"; confirm.textContent = "Bände übernehmen";
      const cancel = document.createElement("button"); cancel.type = "button";
      cancel.className = "ask-panel-cancel-button"; cancel.textContent = "Abbrechen";
      panel.append(text, summary, confirm, cancel); popup.append(panel); document.body.append(popup);
      let attempted = false;
      cancel.addEventListener("click", () => { popup.remove(); if (!attempted) buyButton.disabled = false; resolve(false); });
      confirm.addEventListener("click", async () => {
        if (confirm.disabled) return;
        attempted = true;
      const body = { itemId, confirmedSeriesId: preview.seriesId, firstBand: preview.firstBand,
        lastBand: preview.lastBand, purchaseToken: preview.purchaseToken };
        if (statusId !== null) body.statusId = statusId;
        try {
          const data = await MangaLibFeedback.request(statusId === null ? "/buyItemUpdate" : "/buyItemCreate", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
          }, popup, { button: confirm, validate: result => ["Reihe aktualisiert", "Neue MangaReihe erfolgreich gespeichert",
            "Kauf bereits übernommen"].includes(result.message) });
          popup.remove();
          if (data.message === "Kauf bereits übernommen") {
            MangaLibFeedback.show(document.querySelector("main"), "Kauf bereits übernommen. Es wurde nichts erneut hinzugefügt.");
            resolve(false);
          } else resolve(true);
        } catch { /* Nach unbestätigter Speicherung gesperrt halten, nie automatisch wiederholen. */ }
      });
      confirm.focus();
    });
  }
});
