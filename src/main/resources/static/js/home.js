// Nur absolute Web-URLs verwenden. Kein HTML-/JavaScript-Kontext und keine
// Zugangsdaten; erlaubte Bildhosts bleiben hier bewusst unverändert.
function getSafeBandUrl(value) {
    if (typeof value !== "string" || !/^https?:\/\//i.test(value.trim())
            || /[\u0000-\u0020\u007f\\]/.test(value.trim())) {
        return null;
    }
    try {
        const url = new URL(value.trim());
        if (!["https:", "http:"].includes(url.protocol) || !url.hostname || url.username || url.password) {
            return null;
        }
        return url.href;
    } catch {
        return null;
    }
}

// Daten aus dem Band-JSON sind Text/Werte, niemals HTML oder JavaScript-Code.
function createBandElement(band) {
    const row = document.createElement("div");
    row.className = "band-container";

    const cover = document.createElement("img");
    cover.className = "lazy";
    cover.src = "/svg/filler_img2.png";
    cover.alt = "Band Bild";
    const coverUrl = getSafeBandUrl(band.bildUrl);
    if (coverUrl) {
        cover.dataset.src = coverUrl;
    }

    const title = document.createElement("p");
    title.textContent = `${band.titel}, Band: ${band.bandNr}`;
    const price = document.createElement("p");
    price.textContent = band.totalPreis;

    const mpButton = document.createElement("button");
    mpButton.className = "MP-link";
    const mpIcon = document.createElement("img");
    mpIcon.src = "/svg/MangaPassion_logo.png";
    mpIcon.alt = "MP";
    mpIcon.style.height = "17px";
    mpIcon.style.width = "17px";
    mpButton.append(mpIcon);
    const mpUrl = getSafeBandUrl(band.mpUrl);
    mpButton.disabled = !mpUrl;
    if (mpUrl) {
        mpButton.addEventListener("click", () => window.open(mpUrl, "_blank", "noopener,noreferrer"));
    }

    const readLabel = document.createElement("div");
    readLabel.className = band.istGelesen ? "istGelesen-label checked" : "istGelesen-label";
    const editButton = document.createElement("button");
    editButton.className = "edit-band-button";
    editButton.dataset.id = band.id;
    const editIcon = document.createElement("img");
    editIcon.src = "/svg/edit_icon.svg";
    editIcon.alt = "Edit";
    editIcon.style.height = "17px";
    editIcon.style.width = "17px";
    editButton.append(editIcon);
    editButton.addEventListener("click", () => editBand(editButton.dataset.id));

    row.append(cover, title, price, mpButton, readLabel, editButton);
    return row;
}

// Function to initialize Lazy Loading for images
function initializeLazyLoading() {
    let lazyImages = [].slice.call(document.querySelectorAll("img.lazy"));

    if ("IntersectionObserver" in window) {
        let lazyImageObserver = new IntersectionObserver(function(entries, observer) {
            entries.forEach(function(entry) {
                if (entry.isIntersecting) {
                    let lazyImage = entry.target;
                    // Auch unmittelbar benachbarte, serverseitig gerenderte Lazy-Cover prüfen.
                    lazyImage.src = getSafeBandUrl(lazyImage.dataset.src) || "/svg/filler_img2.png";


                    lazyImage.onload = function() {
                        lazyImage.classList.remove("lazy");
                    };

                    lazyImage.onerror = function() {
                        lazyImage.classList.remove("lazy");
                        console.error("Fehler beim Laden des Bildes:", lazyImage.dataset.src);
                    };

                    lazyImageObserver.unobserve(lazyImage);
                }
            });
        });

        lazyImages.forEach(function(lazyImage) {
            lazyImageObserver.observe(lazyImage);
        });
    } else {
        // Fallback für Browser, die IntersectionObserver nicht unterstützen
        let lazyLoadThrottleTimeout;
        function lazyLoad() {
            if (lazyLoadThrottleTimeout) {
                clearTimeout(lazyLoadThrottleTimeout);
            }

            lazyLoadThrottleTimeout = setTimeout(function() {
                let scrollTop = window.pageYOffset;
                lazyImages.forEach(function(img) {
                    if (img.offsetTop < (window.innerHeight + scrollTop)) {
                        img.src = getSafeBandUrl(img.dataset.src) || "/svg/filler_img2.png";
                        img.classList.remove('lazy');
                    }
                });
                if (lazyImages.length == 0) {
                    document.removeEventListener("scroll", lazyLoad);
                    window.removeEventListener("resize", lazyLoad);
                    window.removeEventListener("orientationChange", lazyLoad);
                }
            }, 20);
        }

        document.addEventListener("scroll", lazyLoad);
        window.addEventListener("resize", lazyLoad);
        window.addEventListener("orientationChange", lazyLoad);
    }
}

// Global function to toggle the bands
function toggleBands(mangaReiheId) {
    console.log(`Toggling bands for ID ${mangaReiheId}`);
    const bandsContainer = document.getElementById('bands-' + mangaReiheId);
    if (bandsContainer) {
        if (bandsContainer.style.display === 'none') {
            // AJAX-Aufruf, um die Bände zu laden
            return fetch('/mangaReihe/' + mangaReiheId + '/bands')
                .then(response => response.json())
                .then(data => {
                    console.log("Fetched data:", data);  // Überprüfen Sie die Daten
                    bandsContainer.replaceChildren(...data.map(createBandElement));

                    // Initialisiere Lazy Loading für die neu geladenen Bilder
                    initializeLazyLoading();

                    bandsContainer.style.display = "block";
                });
        } else {
            bandsContainer.style.display = "none";
        }
    } else {
        console.error("Bands container not found for MangaReihe ID:", mangaReiheId);
    }
}

// Funktion zur Initialisierung des MutationObserver
function initializeObserver() {
    const targetNode = document.getElementById('bands-container');
    if (!targetNode) {
        console.error("Target node for MutationObserver not found");
        return;
    }

    const config = { childList: true, subtree: true };

    const callback = function(mutationsList, observer) {
        for (const mutation of mutationsList) {
            if (mutation.type === 'childList') {
                console.log('A child node has been added or removed.');
                initializeLazyLoading();
            }
        }
    };

    const observer = new MutationObserver(callback);
    observer.observe(targetNode, config);
}

document.addEventListener("DOMContentLoaded", function() {
    // Initialisierung beim Laden der Seite
    initializeLazyLoading();

    // Initialisierung des MutationObserver
    initializeObserver();
});
