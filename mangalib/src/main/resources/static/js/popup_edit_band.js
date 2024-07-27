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
            document.getElementById("manga_reihe_id").value = data.id;
            document.getElementById("band_nr").value = data.bandNr;
            document.getElementById("band_index").value = data.bandIndex;
            document.getElementById("preis").value = data.preis;
            document.getElementById("aenderung_preis").value = data.aenderungPreis;
            document.getElementById("bild_url").value = data.bildUrl;
            document.getElementById("mp_url").value = data.mpUrl;
            document.getElementById("ist_gelesen").checked = data.istGelesen;
            document.getElementById("ist_spezial").checked = data.istSpecial;

            const titleLabel = document.querySelector("#editBaendePopupContainer .title-label");
            titleLabel.textContent = `${data.titel} Band ${data.bandNr}`;

            document.getElementById("editBaendePopupContainer").style.display = "block";
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
        id: bandIdElement.value ? parseInt(bandIdElement.value, 10) : null,
        bandNr: bandNrElement.value ? parseInt(bandNrElement.value, 10) : null,
        bandIndex: bandIndexElement.value ? parseInt(bandIndexElement.value, 10) : null,
        preis: preisElement.value ? parseFloat(preisElement.value.replace(",", ".")) : null,
        aenderungPreis: aenderungPreisElement.value ? parseFloat(aenderungPreisElement.value.replace(",", ".")) : null,
        bildUrl: bildUrlElement.value,
        mpUrl: mpUrlElement.value,
        istGelesen: istGelesenElement.checked,
        istSpezial: istSpezialElement.checked
    };

    console.log('bandData:', bandData);

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
        return response.json();
    })
    .then(data => {
        console.log('Success:', data);
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
