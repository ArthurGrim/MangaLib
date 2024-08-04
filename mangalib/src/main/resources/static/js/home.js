// Function to initialize Lazy Loading for images
function initializeLazyLoading() {
    let lazyImages = [].slice.call(document.querySelectorAll("img.lazy"));

    if ("IntersectionObserver" in window) {
        let lazyImageObserver = new IntersectionObserver(function(entries, observer) {
            entries.forEach(function(entry) {
                if (entry.isIntersecting) {
                    let lazyImage = entry.target;
                    lazyImage.src = lazyImage.dataset.src;


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
                        img.src = img.dataset.src;
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
            fetch('/mangaReihe/' + mangaReiheId + '/bands')
                .then(response => response.json())
                .then(data => {
                    console.log("Fetched data:", data);  // Überprüfen Sie die Daten
                    // Erstelle die Band-Elemente
                    bandsContainer.innerHTML = data.map(band => `
                            <div class="band-container">
                                <img class="lazy" data-src="${band.bildUrl}" src="/svg/filler_img2.png" alt="Band Bild" />
                                <p>${band.titel}, Band: ${band.bandNr}</p>
                                <p>${band.totalPreis}</p>
                                <button class="MP-link" onclick="window.open('${band.mpUrl}', '_blank')">
                                    <img src="/svg/MangaPassion_logo.png" alt="MP" style="height: 17px; width: 17px"/>
                                </button>
                                <div class="istGelesen-label ${band.istGelesen ? 'checked' : ''}"></div>
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
