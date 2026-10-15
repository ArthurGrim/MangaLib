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
    cover.src = mangaLibCoverPlaceholder();
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
                    mangaLibSetCover(lazyImage, getSafeBandUrl(lazyImage.dataset.src));


                    lazyImage.onload = function() {
                        lazyImage.classList.remove("lazy");
                    };

                    lazyImage.addEventListener("error", () => lazyImage.classList.remove("lazy"), { once: true });

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
                        mangaLibSetCover(img, getSafeBandUrl(img.dataset.src));
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

        lazyLoad();
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

document.addEventListener("DOMContentLoaded", function() {
    initializeLazyLoading();
});
