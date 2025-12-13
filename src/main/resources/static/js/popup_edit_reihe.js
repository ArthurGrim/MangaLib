document.addEventListener("DOMContentLoaded", function () {
  // Event Listener für den Edit-Button
  document.querySelectorAll(".edit-button").forEach((button) => {
    button.addEventListener("click", function () {
      var mangaReiheId = this.getAttribute("data-id");
      console.log("MangaReihe ID:", mangaReiheId);
      openEditPopup(mangaReiheId);
    });
  });

  // Event Listener für den MP-Button
  document.querySelectorAll(".MP-button").forEach((button) => {
    button.addEventListener("click", function () {
      var mangaReiheId = this.getAttribute("data-id");
      console.log("MangaReihe ID:", mangaReiheId);
      openMangaPassion(mangaReiheId);
    });
  });

  // Event Listener für den AL-Button
  document.querySelectorAll(".AL-button").forEach((button) => {
    button.addEventListener("click", function () {
      var mangaReiheId = this.getAttribute("data-id");
      console.log("MangaReihe ID:", mangaReiheId);
      openAniList(mangaReiheId);
    });
  });

  console.log("Skript geladen");

  let scrapedData = {};

  // Referenzen auf Elemente
  const editpopupContainer = document.getElementById("editPopupContainer");
  const cancelButton = document.querySelector(".cancel-button-edit");
  const saveButton = document.querySelector(".save-button-edit");
  const autofillButton = document.querySelector(".mp-autofill-button-edit");
  const mangaIndexInput = document.querySelector("#manga_index-edit");

  // Event-Handler, um das Pop-up zu verbergen
  cancelButton.addEventListener("click", function () {
    console.log("Cancel button clicked");
    editpopupContainer.style.display = "none";
  });

  // Referenzen auf Checkboxen, Felder und Labels
  const istEbayPreisCheckbox = document.getElementById("istebaypreis-edit");
  const gesamtpreisAenderungFeld = document.getElementById(
    "gesamtpreis_aenderung-edit"
  );
  const gesamtpreisAenderungLabel = document.querySelector(
    "label[for='gesamtpreis_aenderung-edit']"
  );
  const istSammelbandCheckbox = document.getElementById("istsammelband-edit");
  const sammelbandTypFeld = document.getElementById("sammelband_typ-edit");
  const sammelbandTypLabel = document.querySelector(
    "label[for='sammelband_typ-edit']"
  );

  // Funktion zum Aktualisieren der Sichtbarkeit
  function updateVisibility() {
    console.log(
      "Update Visibility:",
      istEbayPreisCheckbox.checked,
      istSammelbandCheckbox.checked
    );
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

  // Initialer Aufruf, um die anfängliche Sichtbarkeit zu setzen
  updateVisibility();

  // Event-Handler für Checkboxen
  istEbayPreisCheckbox.addEventListener("change", updateVisibility);
  istSammelbandCheckbox.addEventListener("change", updateVisibility);

  function openEditPopup(mangaReiheId) {
    console.log("Edit-Button geklickt für MangaReihe ID: ", mangaReiheId);

    // Senden einer Anfrage an den Server, um die Daten der MangaReihe zu erhalten
    fetch(`/getMangaReiheData/${mangaReiheId}`)
      .then((response) => response.json())
      .then((data) => {
        console.log("Empfangene Daten:", data);
        // Setzen der Werte in die Formularfelder
        document.getElementById("id-edit").textContent = data.id;
        console.log("ID: " + data["id"]);
        document.getElementById("editCoverImage").src =
          data["bildUrl"] || "/svg/filler_img2.png";
        document.getElementById("editTitleLabel").textContent = data.titel;
        document.getElementById("manga_index-edit").value =
          data.mangaIndex ?? "";
        document.getElementById("titel-edit").value = data.titel;
        document.getElementById("status-edit").value = data.statusId;
        document.getElementById("verlag-edit").value = data.verlagId;
        document.getElementById("typ-edit").value = data.typId;
        document.getElementById("format-edit").value = data.formatId;
        document.getElementById("anzahl_baende-edit").value = data.anzahlBaende;
        document.getElementById("preis_pro_band-edit").textContent =
          data.preisProBand;
        document.getElementById("anilist_url-edit").value =
          data.anilistUrl ?? "";
        document.getElementById("coverUrl-edit").value = data.coverUrl ?? "";
        document.getElementById("istgelesen-edit").checked = data.istGelesen;
        document.getElementById("reread-edit").value = data.reread ?? 0;
        document.getElementById("istvergriffen-edit").checked =
          data.istVergriffen;
        document.getElementById("istebaypreis-edit").checked =
          data.istEbayPreis;
        document.getElementById("istsammelband-edit").checked =
          data.istSammelband;
        document.getElementById("gesamtpreis_aenderung-edit").textContent =
          data.gesamtpreisAenderung;
        document.getElementById("sammelband_typ-edit").value =
          data.sammelbandTypId;
        console.log("SammelbandID " + data.sammelbandTypId);
        console.log(
          "Select Element Inhalt:",
          document.getElementById("sammelband_typ-edit").innerHTML
        );

        updateVisibility();
        // Pop-up anzeigen
        document.getElementById("editPopupContainer").style.display = "flex";
      })
      .catch((error) => {
        console.error("Fehler beim Abrufen der MangaReihe-Daten:", error);
      });

    // Weitere Funktionen und Logik...
  }

  function showLoading() {
    document.querySelector(".loading-label").style.display = "block";
  }

  function hideLoading() {
    document.querySelector(".loading-label").style.display = "none";
  }

  function showError() {
    const errorLabel = document.querySelector(".error-label");
    errorLabel.style.display = "block";

    setTimeout(() => {
      errorLabel.style.display = "none";
    }, 2000); // Fehler verschwindet nach 2 Sekunden
  }

  // Event-Handler, für den MangaPassion-Autofill Button
  autofillButton.addEventListener("click", function () {
    console.log("Der MP-Autofill Button wurde gedrückt");
    const mangaIndex = mangaIndexInput.value.trim();

    // Validierung des Manga-Index
    if (!mangaIndex || isNaN(mangaIndex)) {
      alert("Bitte geben Sie einen gültigen Manga-Index ein.");
      return;
    }
    console.log("Der MangaIndex ist", mangaIndex);

    // Anzeigen des Lade-Labels
    showLoading();

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
        console.log("Gescrapte Daten", data);
        // Daten in die Eingabefelder und Selects einfügen
        document.querySelector("#verlag-edit").value = data["verlagId"];
        document.querySelector("#typ-edit").value = data["typId"];
        document.querySelector("#titel-edit").value = data["Titel"];
        document.querySelector("#format-edit").value = data["formatId"];
        document.querySelector("#anzahl_baende-edit").value = data[
          "Deutsche Ausgabe Bände"
        ]
          .replace("+", "")
          .trim();
        document.querySelector("#preis_pro_band-edit").value =
          data["Band 1 Preis"];
        document.querySelector("#editCoverImage").src = data["Band 1 Bild Url"];

        // Verstecken des Lade-Labels
        hideLoading();

        // Entfernen der nicht mehr benötigten Schlüssel
        delete data["verlagId"];
        delete data["typId"];
        delete data["formatId"];

        // Später im Code, wenn Sie Daten hinzufügen:
        Object.keys(data).forEach((key) => {
          scrapedData[key] = data[key];
        });
      })
      .catch((error) => {
        hideLoading();
        showError();
        console.error("Fehler beim Abrufen der Manga-Daten:", error);
      });
  });

  function isNum(value) {
    var reg = /^[0-9]\d*$/;
    return reg.test(value);
  }

  // Event-Handler für den Speichern-Button
  saveButton.addEventListener("click", function (event) {
    event.preventDefault(); // Verhindert das Standardverhalten des Formulars
    console.log("Save-Button geklickt");

    // Eingabefelder und Selects auslesen
    const mangaReiheIdLabel = document.querySelector("#id-edit");
    const mangaReiheId = mangaReiheIdLabel
      ? mangaReiheIdLabel.textContent.trim()
      : null;
    const mangaIndex = document.querySelector("#manga_index-edit").value.trim();
    const statusId = document.querySelector("#status-edit").value;
    const verlagId = document.querySelector("#verlag-edit").value;
    const typId = document.querySelector("#typ-edit").value;
    const formatId = document.querySelector("#format-edit").value;
    const titel = document.querySelector("#titel-edit").value.trim();
    const anzahlBaende = document
      .querySelector("#anzahl_baende-edit")
      .value.trim();
    const anilistUrl = document.querySelector("#anilist_url-edit").value.trim();
    const coverUrl = document.querySelector("#coverUrl-edit").value.trim();
    const istGelesen = document.querySelector("#istgelesen-edit").checked;
    const reread = document.querySelector("#reread-edit").value.trim();
    const istVergriffen = document.querySelector("#istvergriffen-edit").checked;
    const istEbayPreis = document.querySelector("#istebaypreis-edit").checked;
    const istSammelband = document.querySelector("#istsammelband-edit").checked;
    const sammelbandTypId = document.querySelector(
      "#sammelband_typ-edit"
    ).value;

    // Validierung der Werte
    if (!statusId) {
      alert("Bitte wählen Sie einen Status aus.");
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
    if (anilistUrl && !anilistUrl.match(/^https?:\/\/.+$/)) {
      alert("Bitte geben Sie eine gültige AniList-URL ein.");
      return;
    }

    if (coverUrl && !coverUrl.match(/^https?:\/\/.+$/)) {
      alert("Bitte geben Sie eine gültige Cover-URL ein.");
      return;
    }

    if (reread && !isNum(reread)) {
      alert("Reread muss eine positive ganze Zahl sein.");
      return;
    }

    console.log("Ausgelesene Werte:");
    console.log("MangaIndex: ", mangaIndex);
    console.log("VerlagID: ", verlagId);
    console.log("TypId: ", typId);
    console.log("FormatId: ", formatId);
    console.log("Titel: ", titel);
    console.log("Anzahl Baende: ", anzahlBaende);
    console.log("anilistUrl: ", anilistUrl);
    console.log("istGelesen: ", istGelesen);
    console.log("istVergriffen: ", istVergriffen);
    console.log("istEbayPreis: ", istEbayPreis);
    console.log("istSammelband: ", istSammelband);
    console.log("sammelbandTypId: ", sammelbandTypId);
    console.log("reread: ", reread);

    scrapedData["istEdit"] = "true";

    // Objekt erstellen, das gesendet werden soll
    const mangaReiheData = {
      mangaReiheId,
      mangaIndex: mangaIndex ? parseInt(mangaIndex, 10) : null,
      statusId,
      verlagId,
      typId,
      formatId,
      titel,
      anzahlBaende: parseInt(anzahlBaende, 10),
      istGelesen,
      istVergriffen,
      istEbayPreis,
      anilistUrl,
      coverUrl,
      sammelbandTypId: istSammelband ? sammelbandTypId : null,
      reread: reread ? parseInt(reread, 10) : 0,
      scrapedData: scrapedData,
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
        document.querySelector("#editPopupContainer").style.display = "none";
        resetPopupFields();
        window.location.reload();
      })
      .catch((error) => {
        console.error("Error:", error);
      });
  });

  function resetPopupFields() {
    // Zurücksetzen aller Eingabefelder
    document
      .querySelectorAll(
        '.popup-edit input[type="text"], .popup-edit input[type="number"]'
      )
      .forEach((input) => {
        input.value = "";
      });

    // Zurücksetzen aller Select-Elemente
    document.querySelectorAll(".popup-edit select").forEach((select) => {
      select.selectedIndex = 0;
    });

    // Zurücksetzen der Checkboxen
    document
      .querySelectorAll('.popup-edit input[type="checkbox"]')
      .forEach((checkbox) => {
        checkbox.checked = false;
      });

    // Aktualisieren der Sichtbarkeit basierend auf Checkboxen
    updateVisibility();
  }

  // Zum öffnen der entsprechenden Manga Passion Seite zur MangaReihe
  function openMangaPassion(mangaReiheId) {
    console.log("Edit-Button geklickt für MangaReihe ID: ", mangaReiheId);

    // Senden einer Anfrage an den Server, um die Daten der MangaReihe zu erhalten
    fetch(`/getMangaReiheData/${mangaReiheId}`)
      .then((response) => response.json())
      .then((data) => {
        if (data.mangaIndex == null) alert("Kein MangaIndex hinterlegt!");
        else
          window.open(
            "https://www.manga-passion.de/editions/" + data.mangaIndex,
            "_blank"
          );
      })
      .catch((error) => {
        console.error("Fehler beim Abrufen der MangaReihe-Daten:", error);
      });
  }

  //Zum öffnen der entsprechenden AniList Seite zur MangaReihe
  function openAniList(mangaReiheId) {
    console.log("Edit-Button geklickt für MangaReihe ID: ", mangaReiheId);

    // Senden einer Anfrage an den Server, um die Daten der MangaReihe zu erhalten
    fetch(`/getMangaReiheData/${mangaReiheId}`)
      .then((response) => response.json())
      .then((data) => {
        if (data.mangaIndex == null) alert("Kein AniList URL hinterlegt!");
        else window.open(data.anilistUrl, "_blank");
      })
      .catch((error) => {
        console.error("Fehler beim Abrufen der MangaReihe-Daten:", error);
      });
  }
});
