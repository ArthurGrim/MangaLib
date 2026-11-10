document.addEventListener("DOMContentLoaded", function () {
  console.log("Skript geladen");

  let scrapedData = {};

  // Referenzen auf Elemente
  const popupContainer = document.getElementById("popupContainer");
  const addButton = document.getElementById("addButton"); // Geändert zu getElementById
  const cancelButton = document.querySelector(".cancel-button");
  const saveButton = document.querySelector(".save-button");
  const nextId = document.getElementById("id").textContent;
  const autofillButton = document.querySelector(".mp-autofill-button");
  const mangaIndexInput = document.querySelector("#manga_index");
  const importMode = popupContainer.querySelector("#import_mode");
  const volumeCount = popupContainer.querySelector("#anzahl_baende");
  const volumePreview = popupContainer.querySelector("#volume_import_preview");
  const volumeCover = popupContainer.querySelector("#volume_import_cover");
  const volumeDate = popupContainer.querySelector("#volume_import_date");
  const volumeNumber = popupContainer.querySelector("#volume_import_number");
  const volumeTarget = popupContainer.querySelector("#volume_import_target");
  const volumeSeriesTitle = popupContainer.querySelector("#volume_import_series_title");
  let previewIndex = "";
  let seriesVolumeCount = "";
  let seriesTitle = "";
  let seriesPrice = "";
  let importRevision = 0;

  importMode.addEventListener("change", () => {
    const title = popupContainer.querySelector("#titel");
    const price = popupContainer.querySelector("#preis_pro_band");
    if (importMode.value === "volume") {
      seriesVolumeCount = volumeCount.value;
      seriesTitle = title.value;
      seriesPrice = price.value;
      title.value = "";
      price.value = "";
    } else {
      title.value = seriesTitle;
      price.value = seriesPrice;
    }
    mangaIndexInput.value = "";
    scrapedData = {};
    updateImportMode();
  });

  function updateImportMode() {
    importRevision++;
    const singleVolume = importMode.value === "volume";
    volumeCount.value = singleVolume ? "1" : seriesVolumeCount;
    volumeCount.readOnly = singleVolume;
    volumePreview.hidden = !singleVolume;
    previewIndex = "";
    volumeNumber.value = "";
    volumeTarget.value = "";
    volumeSeriesTitle.value = "";
    volumeCover.value = "";
    volumeDate.value = "";
    popupContainer.querySelector("#import_mode_hint").hidden = !singleVolume;
    hideLoading();
  }

  // Event-Handler, um das Pop-up anzuzeigen
  addButton.addEventListener("click", function () {
    console.log("Add-Button geklickt");
    MangaLibFeedback.clear(popupContainer);
    popupContainer.style.display = "flex";
  });

  // Event-Handler, um das Pop-up zu verbergen
  cancelButton.addEventListener("click", function () {
    popupContainer.style.display = "none";
    resetPopupFields();
  });

  function showLoading() {
    popupContainer.querySelector(".loading-label").style.display = 'block';
  }
  
  function hideLoading() {
    popupContainer.querySelector(".loading-label").style.display = 'none';
  }
  
  function showError() {
    const errorLabel = popupContainer.querySelector(".error-label");
    errorLabel.style.display = 'block';
    setTimeout(() => {
      errorLabel.style.display = 'none';
    }, 2000);
  }

  // Event-Handler, für den MangaPassion-Autofill Button
  autofillButton.addEventListener("click", function () {
    const singleVolume = importMode.value === "volume";
    const mangaIndex = mangaIndexInput.value.trim();

    // Validierung des Manga-Index
    if (!mangaIndex || (singleVolume ? !/^[1-9][0-9]*$/.test(mangaIndex) : isNaN(mangaIndex))) {
      MangaLibFeedback.show(popupContainer, "Bitte geben Sie einen gültigen Manga-Index ein.");
      return;
    }

    const revision = ++importRevision;
    if (singleVolume) {
      previewIndex = "";
      volumeTarget.value = "";
      volumeSeriesTitle.value = "";
    }
    MangaLibFeedback.clear(popupContainer);
    // Anzeigen des Lade-Labels
    showLoading();

    // Senden des Manga-Index an den Web Scraper
    const endpoint = singleVolume ? `/autofillBandData/${mangaIndex}?importPreview` : "/scrape";
    const options = singleVolume ? { method: "POST" } : {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ mangaIndex: mangaIndex }),
    };
    MangaLibFeedback.request(endpoint, options, popupContainer, { persist: false })
      .then((data) => {
        if (revision !== importRevision) return;
        if (singleVolume) {
          hideLoading();
          if (mangaIndexInput.value.trim() !== mangaIndex) return;
          popupContainer.querySelector("#titel").value = data.Titel ?? "";
          popupContainer.querySelector("#preis_pro_band").value = data.Preis ?? "";
          volumeCover.value = data.BildUrl ?? "";
          volumeDate.value = data.Veröffentlichung ?? data.Veröffentlichungsdatum ?? data.Erscheinungsdatum ?? "";
          volumeNumber.value = data.Bandnummer ?? "";
          if (data.Bandnummer && data.Titel) {
            volumeSeriesTitle.value = data.Titel.replace(/,\s*Band\s+[1-9][0-9]*$/, "");
          }
          previewIndex = data.Titel ? mangaIndex : "";
          return;
        }
        // Daten in die Eingabefelder und Selects einfügen
        document.querySelector("#verlag").value = data["verlagId"];
        document.querySelector("#typ").value = data["typId"];
        document.querySelector("#titel").value = data["Titel"];
        document.querySelector("#format").value = data["formatId"];
        document.querySelector("#anzahl_baende").value = data[
          "Deutsche Ausgabe Bände"
        ]
          .replace("+", "")
          .trim();
        document.querySelector("#preis_pro_band").value = data["Band 1 Preis"];

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
        if (revision !== importRevision) return;
        hideLoading();
        if (singleVolume && error.status === 409) {
          MangaLibFeedback.show(popupContainer, "Dieser Manga-Passion-Band ist bereits in der Bibliothek vorhanden.");
          return;
        }
        showError();
        // Keine Rohfehler oder Antwortbodys anzeigen.
      });
  });

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

  // Angenommen, die nächste ID wird vom Server als 'nextId' übergeben
  document.getElementById("id").textContent = nextId;

  function isNum(value) {
    var reg = /^[0-9]\d*$/;
    return reg.test(value);
  }

  // Event-Handler für den Speichern-Button
  saveButton.addEventListener("click", function (event) {
    event.preventDefault(); // Verhindert das Standardverhalten des Formulars
    MangaLibFeedback.clear(popupContainer);
    console.log("Save-Button geklickt");

    // Eingabefelder und Selects auslesen
    const mangaIndex = document.querySelector("#manga_index").value.trim();
    const statusId = document.querySelector("#status").value;
    const verlagId = document.querySelector("#verlag").value;
    const typId = document.querySelector("#typ").value;
    const formatId = document.querySelector("#format").value;
    const titel = document.querySelector("#titel").value.trim();
    const anzahlBaende = document.querySelector("#anzahl_baende").value.trim();
    const preisProBandString = document
      .querySelector("#preis_pro_band")
      .value.trim();
    const preisProBand = preisProBandString.replace(",", ".");
    if (importMode.value === "volume") {
      if (!previewIndex || previewIndex !== mangaIndex || !volumeTarget.value ||
          !/^[1-9][0-9]*$/.test(volumeNumber.value)) {
        MangaLibFeedback.show(popupContainer, "Bitte Band laden, Bandnummer prüfen und Zielreihe wählen.");
        return;
      }
      save({
        importMode: "volume", bandIndex: previewIndex, bandNumber: volumeNumber.value,
        targetSeriesId: volumeTarget.value === "new" ? null : volumeTarget.value,
        coverUrl: volumeCover.value.trim(), anzahlBaende: 1, preisProBand,
        statusId, verlagId, typId, formatId, titel: volumeSeriesTitle.value.trim()
      });
      return;
    }
    const anilistUrl = document.querySelector("#anilist_url").value.trim();
    const istGelesen = document.querySelector("#istgelesen").checked;
    const reread = document.querySelector("#reread").value.trim();
    const istVergriffen = document.querySelector("#istvergriffen").checked;
    const istEbayPreis = document.querySelector("#istebaypreis").checked;
    const gesamtpreisAenderungString = document
      .querySelector("#gesamtpreis_aenderung")
      .value.trim();
    const gesamtpreisAenderung = gesamtpreisAenderungString.replace(",", ".");
    const istSammelband = document.querySelector("#istsammelband").checked;
    const sammelbandTypId = document.querySelector("#sammelband_typ").value;

    // Validierung der Werte
    if (mangaIndex && !isNum(mangaIndex)) {
      MangaLibFeedback.show(popupContainer, "MangaIndex muss eine positive ganze Zahl sein.");
      return;
    }
    if (!statusId) {
      MangaLibFeedback.show(popupContainer, "Bitte wählen Sie einen Status aus.");
      return;
    }
    if (!verlagId) {
      MangaLibFeedback.show(popupContainer, "Bitte wählen Sie einen Verlag aus.");
      return;
    }
    if (!typId) {
      MangaLibFeedback.show(popupContainer, "Bitte wählen Sie einen Typ aus.");
      return;
    }
    if (!formatId) {
      MangaLibFeedback.show(popupContainer, "Bitte wählen Sie ein Format aus.");
      return;
    }
    if (!titel) {
      MangaLibFeedback.show(popupContainer, "Bitte geben Sie einen Titel ein.");
      return;
    }
    if (!isNum(anzahlBaende)) {
      MangaLibFeedback.show(popupContainer, "Anzahl der Bände muss eine positive ganze Zahl sein.");
      return;
    }
    if (!/^\d+(\.\d{1,2})?$/.test(preisProBand)) {
      MangaLibFeedback.show(popupContainer,
        "Preis pro Band muss eine Zahl mit maximal zwei Dezimalstellen sein."
      );
      return;
    }
    if (istEbayPreis && !/^[-+]?\d+(\.\d+)?$/.test(gesamtpreisAenderung)) {
      MangaLibFeedback.show(popupContainer,
        "Der eBay-Preis muss eine gültige Dezimalzahl sein (Vorzeichen relevant)."
      );
      return;
    }

    if (anilistUrl && !anilistUrl.match(/^https?:\/\/.+$/)) {
      MangaLibFeedback.show(popupContainer, "Bitte geben Sie eine gültige AniList-URL ein.");
      return;
    }
    if (reread && !isNum(reread)) {
      MangaLibFeedback.show(popupContainer, "Reread muss eine positive ganze Zahl sein.");
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
    console.log("istGelesen", istGelesen);
    console.log("istVergriffen: ", istVergriffen);
    console.log("istEbayPreis: ", istEbayPreis);
    console.log("gesamtpreisAenderung: ", gesamtpreisAenderung);
    console.log("istSammelband: ", istSammelband);
    console.log("sammelbandTypId: ", sammelbandTypId);
    console.log("reread: ", reread);

    scrapedData["istEdit"] = "false";

    // Objekt erstellen, das gesendet werden soll
    const mangaReiheData = {
      importMode: importMode.value,
      mangaIndex: mangaIndex ? parseInt(mangaIndex, 10) : null,
      statusId,
      verlagId,
      typId,
      formatId,
      titel,
      anzahlBaende: parseInt(anzahlBaende, 10),
      preisProBand,
      istGelesen,
      istVergriffen,
      istEbayPreis,
      gesamtpreisAenderung: istEbayPreis ? gesamtpreisAenderung : "0",
      anilistUrl,
      sammelbandTypId: istSammelband ? sammelbandTypId : null,
      reread: reread ? parseInt(reread, 10) : null,
      scrapedData: scrapedData,
    };

    // Loggen der zu sendenden Daten
    console.log("Zu sendende Daten:", mangaReiheData);

    // Daten senden mit Fetch-API
    save(mangaReiheData);
  });

  function save(data) {
    MangaLibFeedback.request("/addMangaReihe", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(data),
    }, popupContainer)
      .then((data) => {
        console.log("Antwort vom Server:", data); // Erfolgsmeldung oder Datenverarbeitung
        // Schließen des Popups
        document.querySelector("#popupContainer").style.display = "none";
        resetPopupFields();
        window.location.reload();
      })
      .catch(error => {
        if (data.importMode === "volume" && error.status === 409) {
          MangaLibFeedback.show(popupContainer, "Der Band ist bereits vorhanden oder die Zielreihe ist nicht eindeutig verfügbar.");
        }
      });
  }

  function resetPopupFields() {
    scrapedData = {};
    seriesVolumeCount = "";
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
    updateImportMode();
  }
});
