document.addEventListener("DOMContentLoaded", function () {
  let scrapedData = {};

  const deleteButtons = document.querySelectorAll(".delete-button");

  deleteButtons.forEach((button) => {
    button.addEventListener("click", function (event) {
      // Auslesen der data-id vom Bild-Element innerhalb des Buttons
      const itemId = event.currentTarget.getAttribute('data-id');
      console.log("Deleting item with id:", itemId);

      fetch(`/items/${itemId}`, {
        method: "DELETE",
      })
        .then((response) => {
          if (response.ok) {
            // Datensatz erfolgreich gelöscht
            console.log("Item erfolgreich gelöscht");
            window.location.reload();
          } else {
            // Fehler beim Löschen des Datensatzes
            console.error("Fehler beim Löschen des Items");
          }
        })
        .catch((error) => console.error("Fehler:", error));
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

  // Event-Listener für den Add-Button
  var addButton = document.getElementById("add-button");
  if (addButton) {
    addButton.addEventListener("click", function () {
      const monthName = document.getElementById("monats-name").textContent;
      const monthNumber = convertMonthNameToNumber(monthName);
      // Setzen des Monats im Pop-up
      document.querySelector(".monat-select").value = monthNumber;
      // Popup anzeigen
      var popup = document.getElementById("popupContainer");
      if (popup) {
        popup.style.display = "flex";
      }
    });
  }

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
      alert("MangaIndex muss eine positive ganze Zahl sein.");
      return;
    }
    if (!verlagId) {
      alert("Bitte wählen Sie einen Verlag aus.");
      return;
    }
    if (!typId) {
      alert("Bitte wählen Sie einen Typ aus.");
      return;
    }
    if (!formatId) {
      alert("Bitte wählen Sie ein Format aus.");
      return;
    }
    if (!titel) {
      alert("Bitte geben Sie einen Titel ein.");
      return;
    }
    if (!isNum(anzahlBaende)) {
      alert("Anzahl der Bände muss eine positive ganze Zahl sein.");
      return;
    }
    if (!/^\d+(\.\d{1,2})?$/.test(preisProBand)) {
      alert(
        "Preis pro Band muss eine Zahl mit maximal zwei Dezimalstellen sein."
      );
      return;
    }
    if (istEbayPreis && !/^[-+]?\d+(\.\d+)?$/.test(gesamtpreisAenderung)) {
      alert(
        "Der eBay-Preis muss eine gültige Dezimalzahl sein (Vorzeichen relevant)."
      );
      return;
    }

    if (anilistUrl && !anilistUrl.match(/^https?:\/\/.+$/)) {
      alert("Bitte geben Sie eine gültige AniList-URL ein.");
      return;
    }

    if(!tag || !monat || !jahr){
      alert("Bitte geben Sie ein gültiges Erscheinungsdatum ein.");
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
    fetch("/addToEinkaufsliste", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(mangaReiheData),
    })
      .then((response) => {
        console.log("Serverantwort erhalten");
        return response.json();
      })
      .then((data) => {
        console.log("Antwort vom Server:", data); // Erfolgsmeldung oder Datenverarbeitung
        // Schließen des Popups
        document.querySelector("#popupContainer").style.display = "none";
        resetPopupFields();
        window.location.reload();
      })
      .catch((error) => {
        console.error("Error:", error);
      });
  });

  const autofillButton = document.querySelector(".mp-autofill-button");
  const mangaIndexInput = document.querySelector("#manga_index");

  // Event-Handler, für den MangaPassion-Autofill Button
  autofillButton.addEventListener("click", function () {
    const mangaIndex = mangaIndexInput.value.trim();
    const loadingLabel = document.querySelector(".loading-label");

    // Validierung des Manga-Index
    if (!mangaIndex || isNaN(mangaIndex)) {
      alert("Bitte geben Sie einen gültigen Manga-Index ein.");
      return;
    }

    // Anzeigen des Lade-Labels
    loadingLabel.style.display = "block";

    // Senden des Manga-Index an den Web Scraper
    fetch("/scrape", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ mangaIndex: mangaIndex }),
    })
      .then((response) => response.json())
      .then((data) => {
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
        loadingLabel.style.display = "none";

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
        console.error("Fehler beim Abrufen der Manga-Daten:", error);
      });
  });
});
