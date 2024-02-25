document.addEventListener("DOMContentLoaded", function () {
  console.log("Skript geladen");

  // Referenzen auf Elemente
  const popupContainer = document.getElementById("popupContainer");
  const addButton = document.getElementById("addButton"); // Geändert zu getElementById
  const cancelButton = document.querySelector(".cancel-button");
  const saveButton = document.querySelector(".save-button");
  const nextId = document.getElementById("id").textContent;

  // Event-Handler, um das Pop-up anzuzeigen
  addButton.addEventListener("click", function () {
    console.log("Add-Button geklickt");
    popupContainer.style.display = "flex";
  });

  // Event-Handler, um das Pop-up zu verbergen
  cancelButton.addEventListener("click", function () {
    popupContainer.style.display = "none";
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
    console.log("Save-Button geklickt");

    // Eingabefelder und Selects auslesen
    const mangaIndex = document.querySelector("#manga_index").value.trim();
    const statusId = document.querySelector("#status").value;
    const verlagId = document.querySelector("#verlag").value;
    const typId = document.querySelector("#typ").value;
    const formatId = document.querySelector("#format").value;
    const titel = document.querySelector("#titel").value.trim();
    const anzahlBaende = document.querySelector("#anzahl_baende").value.trim();
    const preisProBand = document.querySelector("#preis_pro_band").value.trim();
    const anilistUrl = document.querySelector("#anilist_url").value.trim();
    const istVergriffen = document.querySelector("#istvergriffen").checked;
    const istEbayPreis = document.querySelector("#istebaypreis").checked;
    const gesamtpreisAenderung = document
      .querySelector("#gesamtpreis_aenderung")
      .value.trim();
    const istSammelband = document.querySelector("#istsammelband").checked;
    const sammelbandTypId = document.querySelector("#sammelband_typ").value;

    // Validierung der Werte
    if (mangaIndex && !isNum(mangaIndex)) {
      alert("MangaIndex muss eine positive ganze Zahl sein.");
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

    // Objekt erstellen, das gesendet werden soll
    const mangaReiheData = {
      mangaIndex: mangaIndex ? parseInt(mangaIndex, 10) : null,
      statusId,
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
      sammelbandTypId: istSammelband ? sammelbandTypId : null
  };

    // Loggen der zu sendenden Daten
    console.log("Zu sendende Daten:", mangaReiheData);

    // Daten senden mit Fetch-API
    fetch("/addMangaReihe", {
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
      })
      .catch((error) => {
        console.error("Error:", error);
      });
  });
});
