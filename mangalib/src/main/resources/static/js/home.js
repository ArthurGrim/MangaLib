// home.js
function toggleBands(mangaReiheId) {
  const bandsContainer = document.getElementById('bands-' + mangaReiheId);
  if (bandsContainer) {
    if (bandsContainer.style.display === 'none') {
      // AJAX-Aufruf, um die Bände zu laden
      fetch('/mangaReihe/' + mangaReiheId + '/bands')
        .then(response => response.json())
        .then(data => {
          // Erstelle die Band-Elemente
          bandsContainer.innerHTML = data.map(band => {
            const bandId = band.id || band.bandNr; // Fallback zu bandNr, wenn id nicht vorhanden
            return `
              <div class="band-container">
                <img src="${band.bildUrl}" alt="Band Bild" />
                <p>Band: ${band.bandNr}</p>
                <p>Preis: ${parseFloat(band.preis).toFixed(2)} €</p>
                <button class="MP-link" onclick="window.open('${band.mpUrl}', '_blank')">
                  <img src="/svg/MangaPassion_logo.png" alt="MP" style="height: 17px; width: 17px"/>
                </button>
                <div class="istGelesen-checkbox">
                  <input type="checkbox" id="checkbox-${mangaReiheId}-${bandId}" ${band.istGelesen ? 'checked' : ''}/>
                  <label for="checkbox-${mangaReiheId}-${bandId}"></label>
                </div>
                <button class="edit-button" id="edit-button-${bandId}" onclick="editBand(${bandId})">
                  <img src="/svg/edit_icon.svg" alt="Edit" style="height: 17px; width: 17px"/>
                </button>
              </div>
            `;
          }).join("");
          bandsContainer.style.display = "block";
        });
    } else {
      bandsContainer.style.display = "none";
    }
  } else {
    console.error("Bands container not found for MangaReihe ID:", mangaReiheId);
  }
}

// Funktion zum Bearbeiten eines Bandes
function editBand(bandId) {
  // Hier können Sie den Code zum Bearbeiten eines Bandes hinzufügen
  console.log("Edit Band ID:", bandId);
}
