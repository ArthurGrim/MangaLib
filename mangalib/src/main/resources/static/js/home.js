function toggleBands(mangaReiheId) {
  const bandsContainer = document.getElementById('bands-' + mangaReiheId);
  if (bandsContainer) {
      if (bandsContainer.style.display === 'none') {
          // AJAX-Aufruf, um die Bände zu laden
          fetch('/mangaReihe/' + mangaReiheId + '/bands')
              .then(response => response.json())
              .then(data => {
                  // Erstelle die Band-Elemente
                  bandsContainer.innerHTML = data.map(band => `
                        <div class="band-container">
                            <img src="${band.bildUrl}" alt="Band Bild" />
                            <p>${band.titel}, Band: ${band.bandNr}</p>
                            <p>${parseFloat(band.preis).toFixed(2)} €</p>
                            <button class="MP-link" onclick="window.open('${band.mpUrl}', '_blank')">
                                <img src="/svg/MangaPassion_logo.png" alt="MP" style="height: 17px; width: 17px"/>
                            </button>
                            <div class="istGelesen-checkbox">
                                <input type="checkbox" id="checkbox-${mangaReiheId}-${band.id}" ${band.istGelesen ? 'checked' : ''}/>
                                <label for="checkbox-${mangaReiheId}-${band.id}"></label>
                            </div>
                            <button class="edit-band-button" data-id="${band.id}">
                                <img src="/svg/edit_icon.svg" alt="Edit" style="height: 17px; width: 17px"/>
                            </button>
                        </div>
                    `
            ).join("");

            // Binde den Klick-Event für die neu erzeugten edit-band-buttons
            document.querySelectorAll('.edit-band-button').forEach(button => {
                button.addEventListener('click', function() {
                    const bandId = this.getAttribute('data-id');
                    console.log("edit-band-button clicked with data-id:", bandId); // Log hinzugefügt
                    editBand(bandId);
                });
            });

            bandsContainer.style.display = "block";
        });
    } else {
      bandsContainer.style.display = "none";
    }
  } else {
    console.error("Bands container not found for MangaReihe ID:", mangaReiheId);
  }
}
