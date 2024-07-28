document.addEventListener("DOMContentLoaded", () => {
    const closeButton = document.getElementById("editBandCloseButton");

    if (closeButton) {
        closeButton.addEventListener("click", () => {
            document.getElementById("editBaendePopupContainer").style.display = "none";
        });
    } else {
        console.error("Close button for edit band popup not found");
    }
});

function editBand(bandId) {
    console.log("Editing band with ID:", bandId);
    fetch(`/getBand/${bandId}`) // Korrigieren Sie den Pfad hier
        .then(response => response.json())
        .then(data => {
            console.log("Fetched data for band:", data);
            document.getElementById("editBandCoverImage").src = data.bildUrl;
            document.getElementById("manga_reihe_id").textContent = data.id;
            document.getElementById("band_nr").textContent = data.bandNr;
            document.getElementById("band_index").value = data.bandIndex;
            document.getElementById("preis").value = data.preis;
            document.getElementById("aenderung_preis").value = data.aenderungPreis;
            document.getElementById("bild_url").value = data.bildUrl;
            document.getElementById("mp_url").value = data.mpUrl;
            document.getElementById("ist_gelesen").checked = data.istGelesen;
            document.getElementById("ist_spezial").checked = data.istSpecial;

            const titleLabel = document.querySelector("#editBaendePopupContainer .title-label");
            titleLabel.textContent = `${data.titel} Band ${data.bandNr}`;

            document.getElementById("editBaendePopupContainer").style.display = "flex";
        })
        .catch(error => console.error("Error fetching band data:", error));
}

function saveBand() {
    const bandIdElement = document.getElementById("manga_reihe_id");
    const bandNrElement = document.getElementById("band_nr");
    const bandIndexElement = document.getElementById("band_index");
    const preisElement = document.getElementById("preis");
    const aenderungPreisElement = document.getElementById("aenderung_preis");
    const bildUrlElement = document.getElementById("bild_url");
    const mpUrlElement = document.getElementById("mp_url");
    const istGelesenElement = document.getElementById("ist_gelesen");
    const istSpezialElement = document.getElementById("ist_spezial");

    const bandData = {
        id: bandIdElement.value,
        bandNr: bandNrElement.value,
        bandIndex: bandIndexElement.value,
        preis: preisElement.value,
        aenderungPreis: aenderungPreisElement.value,
        bildUrl: bildUrlElement.value,
        mpUrl: mpUrlElement.value,
        istGelesen: istGelesenElement.checked,
        istSpecial: istSpezialElement.checked
    };

    fetch('/editBand', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        },
        body: JSON.stringify(bandData)
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Network response was not ok ' + response.statusText);
        }
        if (response.status === 204) {
            return null; // No content to parse
        }
        return response.json();
    })
    .then(data => {
        if (data) {
            console.log('Success:', data);
        }
        // Schließen des Popups
        document.querySelector("#editBaendePopupContainer").style.display = "none";
        // Seite neu laden
        window.location.reload();
    })
    .catch(error => {
        console.error('Error updating band:', error);
    });
}

function autofillBandData() {
    const bandIndex = document.getElementById("band_index").value;

    fetch(`/autofillBandData/${bandIndex}`)
        .then(response => response.json())
        .then(data => {
            console.log("Autofilled data:", data); // Log hinzugefügt
            document.getElementById("bild_url").value = data.bildUrl;
            document.getElementById("preis").value = data.preis;
            document.getElementById("mp_url").value = data.mpUrl;
        })
        .catch(error => console.error("Error autofilling band data:", error));
}

function closeEditBandPopup() {
    document.getElementById("editBaendePopupContainer").style.display = "none";
}
