function editBand(bandId) {
  console.log("Editing band with ID:", bandId);
  fetch(`/getBand/${bandId}`) // Korrigieren Sie den Pfad hier
    .then((response) => response.json())
    .then((data) => {
      console.log("Fetched data for band:", data);
      mangaLibSetCover(document.getElementById("editBandCoverImage"), data.bildUrl);
      document.getElementById("manga_reihe_id").textContent = data.id;
      document.getElementById("band_nr").textContent = data.bandNr;
      document.getElementById("band_index").value = data.bandIndex;
      document.getElementById("preis").value = parseFloat(data.preis)
        .toFixed(2)
        .replace(".", ",");
      document.getElementById("aenderung_preis").value = parseFloat(
        data.aenderungPreis
      )
        .toFixed(2)
        .replace(".", ",");
      document.getElementById("bild_url").value = data.bildUrl;
      document.getElementById("mp_url").value = data.mpUrl;
      document.getElementById("ist_gelesen").checked = data.istGelesen;
      document.getElementById("ist_spezial").checked = data.istSpecial;
      document.getElementById("reread_band").value = data.reread;

      const titleLabel = document.querySelector(
        "#editBaendePopupContainer .title-label"
      );
      titleLabel.textContent = `${data.titel} Band ${data.bandNr}`;

      MangaLibFeedback.clear(document.getElementById("editBaendePopupContainer"));
      document.getElementById("editBaendePopupContainer").style.display =
        "flex";
    })
    .catch((error) => console.error("Error fetching band data:", error));
}

function saveBand() {
  MangaLibFeedback.clear(document.getElementById("editBaendePopupContainer"));
  const bandIdElement = document.getElementById("manga_reihe_id");
  const bandNrElement = document.getElementById("band_nr");
  const bandIndexElement = document.getElementById("band_index");
  const preisElement = document.getElementById("preis");
  const aenderungPreisElement = document.getElementById("aenderung_preis");
  const bildUrlElement = document.getElementById("bild_url");
  const mpUrlElement = document.getElementById("mp_url");
  const istGelesenElement = document.getElementById("ist_gelesen");
  const istSpezialElement = document.getElementById("ist_spezial");
  const reread = document.getElementById("reread_band");

  // Validierung
  if (!bandNrElement.textContent.trim()) {
    MangaLibFeedback.show(document.getElementById("editBaendePopupContainer"), "Band Nr darf nicht leer sein.");
    return;
  }

  if (
    !preisElement.value.trim() ||
    isNaN(parseFloat(preisElement.value.replace(",", ".")))
  ) {
    MangaLibFeedback.show(document.getElementById("editBaendePopupContainer"), "Preis muss eine gültige Zahl sein.");
    return;
  }

  if (
    bildUrlElement.value.trim() &&
    !bildUrlElement.value.match(/^https?:\/\/.+$/)
  ) {
    MangaLibFeedback.show(document.getElementById("editBaendePopupContainer"), "Bitte geben Sie eine gültige Bild-URL ein.");
    return;
  }

  if (
    mpUrlElement.value.trim() &&
    !mpUrlElement.value.match(/^https?:\/\/.+$/)
  ) {
    MangaLibFeedback.show(document.getElementById("editBaendePopupContainer"), "Bitte geben Sie eine gültige MP-URL ein.");
    return;
  }

  // Preis und ÄnderungsPreis formatieren
  let preis = preisElement.value.trim().replace(",", ".");
  let aenderungPreis = aenderungPreisElement.value.trim().replace(",", ".");
  if (aenderungPreis === "0") {
    aenderungPreis = "0";
  }

  const bandData = {
    id: bandIdElement.textContent,
    bandNr: bandNrElement.textContent,
    bandIndex: bandIndexElement.value,
    preis: parseFloat(preis).toFixed(2), // Formatierung auf zwei Dezimalstellen
    aenderungPreis: parseFloat(aenderungPreis).toFixed(2), // Formatierung auf zwei Dezimalstellen
    bildUrl: bildUrlElement.value,
    mpUrl: mpUrlElement.value,
    istGelesen: istGelesenElement.checked,
    istSpecial: istSpezialElement.checked,
    reread: reread.value,
  };

  MangaLibFeedback.request("/editBand", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(bandData),
  }, document.getElementById("editBaendePopupContainer"), { empty: 204 })
    .then((data) => {
      if (data) {
        console.log("Success:", data);
      }
      // Schließen des Popups
      document.querySelector("#editBaendePopupContainer").style.display =
        "none";
      // Seite neu laden
      window.location.reload();
    })
    .catch(() => {}); // Eingaben und Dialog bleiben erhalten.
}

function showLoading() {
  document.getElementById("editBaendePopupContainer").querySelector(".loading-label-band-edit").style.display = "block";
}

function hideLoading() {
  document.getElementById("editBaendePopupContainer").querySelector(".loading-label-band-edit").style.display = "none";
}

function showError() {
  const errorLabel = document.getElementById("editBaendePopupContainer").querySelector(".error-label");
  errorLabel.style.display = "block";

  setTimeout(() => {
    errorLabel.style.display = "none";
  }, 2000); // Fehler verschwindet nach 2 Sekunden
}

function autofillBandData() {
  const bandIndex = document.getElementById("band_index").value;

  // Validierung des Manga-Index
  if (!bandIndex || isNaN(bandIndex)) {
    MangaLibFeedback.show(document.getElementById("editBaendePopupContainer"), "Bitte geben Sie einen gültigen Manga-Index ein.");
    return;
  }
  console.log("Der MangaIndex ist", bandIndex);

  MangaLibFeedback.clear(document.getElementById("editBaendePopupContainer"));
  // Anzeigen des Lade-Labels
  showLoading();

  MangaLibFeedback.request(`/autofillBandData/${bandIndex}`, { method: "POST" }, document.getElementById("editBaendePopupContainer"), { persist: false })
    .then((data) => {
      console.log("Autofilled data:", data); // Log hinzugefügt
      document.getElementById("bild_url").value = data.BildUrl; // Schlüssel angepasst
      mangaLibSetCover(document.getElementById("editBandCoverImage"), data.BildUrl); // Schlüssel angepasst
      document.getElementById("preis").value = data["Preis"];
      document.getElementById("mp_url").value = data.mpUrl;
      hideLoading();
    })
    .catch((error) => {
      hideLoading();
      showError();
      // Keine Rohfehler oder Antwortbodys anzeigen.
    });
}

function closeEditBandPopup() {
  document.getElementById("editBaendePopupContainer").style.display = "none";
}
