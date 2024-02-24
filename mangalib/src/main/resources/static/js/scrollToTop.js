// Funktion zum Scrollen nach oben
function scrollToTop() {
    window.scrollTo({top: 0, behavior: 'smooth'});
}

// Funktion zum Anzeigen des Buttons, wenn der Benutzer nach unten scrollt
function handleScroll() {
    const scrollToTopButtonContainer = document.querySelector('.scrollToTopButton');
    if (document.documentElement.scrollTop > 200) { // Button anzeigen, wenn 200px gescrollt wurde
        scrollToTopButtonContainer.style.display = 'block';
    } else {
        scrollToTopButtonContainer.style.display = 'none';
    }
}

// Event Listener für das Scroll-Ereignis
window.addEventListener('scroll', handleScroll);