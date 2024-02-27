document.addEventListener("DOMContentLoaded", function() {
  document.querySelectorAll(".edit-button").forEach((button) => {
      button.addEventListener("click", function() {
          var mangaReiheId = this.getAttribute("data-id");
          console.log("MangaReihe ID:", mangaReiheId);
          openEditPopup(mangaReiheId);
      });
  
  });
  console.log("Skript geladen");

  let scrapedData = null;

  // Referenzen auf Elemente
  const editpopupContainer = document.getElementById("editPopupContainer");
  const cancelButton = document.querySelector(".cancel-button-edit");
  const saveButton = document.querySelector(".save-button-edit");
  const autofillButton = document.querySelector(".mp-autofill-button-edit");
  const mangaIndexInput = document.querySelector("#manga_index");
  

  // Event-Handler, um das Pop-up zu verbergen
  cancelButton.addEventListener("click", function () {
    console.log("Cancel button clicked");
    editpopupContainer.style.display = "none";
  });

  // Referenzen auf Checkboxen, Felder und Labels
  const istEbayPreisCheckbox = document.getElementById("istebaypreis-edit");
  const gesamtpreisAenderungFeld = document.getElementById("gesamtpreis_aenderung-edit");
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
    console.log("Update Visibility:", istEbayPreisCheckbox.checked, istSammelbandCheckbox.checked);
    gesamtpreisAenderungFeld.style.display = istEbayPreisCheckbox.checked ? "block" : "none";
    gesamtpreisAenderungLabel.style.display = istEbayPreisCheckbox.checked ? "block" : "none";

    sammelbandTypFeld.style.display = istSammelbandCheckbox.checked ? "block" : "none";
    sammelbandTypLabel.style.display = istSammelbandCheckbox.checked ? "block" : "none";
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
        document.getElementById("manga_index-edit").value = data.mangaIndex;
        document.getElementById("titel-edit").value = data.titel;
        document.getElementById("status-edit").value = data.statusId;
        document.getElementById("verlag-edit").value = data.verlagId;
        document.getElementById("typ-edit").value = data.typId;
        document.getElementById("format-edit").value = data.formatId;
        document.getElementById("anzahl_baende-edit").value = data.anzahlBaende;
        document.getElementById("preis_pro_band-edit").value = data.preisProBand;
        document.getElementById("anilist_url-edit").value = data.anilistUrl;
        document.getElementById("istvergriffen-edit").checked = data.istVergriffen;
        document.getElementById("istebaypreis-edit").checked = data.istEbayPreis;
        document.getElementById("istsammelband-edit").checked = data.istSammelband;
        document.getElementById("gesamtpreis_aenderung-edit").value = data.gesamtpreisAenderung;
        document.getElementById("sammelband_typ-edit").value = data.sammelbandTypId;
        console.log("SammelbandID " + data.sammelbandTypId);
        console.log("Select Element Inhalt:", document.getElementById("sammelband_typ-edit").innerHTML);

        updateVisibility();
        // Pop-up anzeigen
        document.getElementById("editPopupContainer").style.display = "flex";
      })
      .catch((error) => {
        console.error("Fehler beim Abrufen der MangaReihe-Daten:", error);
      });

    // Weitere Funktionen und Logik...
  }

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
        scrapedData = JSON.parse(JSON.stringify(data));
      })
      .catch((error) => {
        console.error("Fehler beim Abrufen der Manga-Daten:", error);
      });
  });
});
