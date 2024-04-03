document.addEventListener("DOMContentLoaded", function() {
    fetch('/api/statistik/bandKategorien')
        .then(response => response.json())
        .then(data => {
            const ctx = document.getElementById('bandKategorienStatistik').getContext('2d');
            const bandKategorienChart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: ['1 Band', '2-5 Bände', '6-10 Bände', '11-20 Bände', '21-50 Bände', '51-100 Bände', '101+ Bände'],
                    datasets: [{
                        label: 'Anzahl der Reihen',
                        data: [
                            data.reihenMitEinemBand,
                            data.reihenMitZweiBisFuenfBaenden,
                            data.reihenMitSechsBisZehnBaenden,
                            data.reihenMitElfBisZwanzigBaenden,
                            data.reihenMitEinundzwanzigBisFuenfzigBaenden,
                            data.reihenMitEinundfuenfzigBisHundertBaenden,
                            data.reihenMitMehrAlsHundertBaenden
                        ],
                        backgroundColor: [
                            'rgba(255, 99, 132, 0.2)',
                            // Farben für weitere Balken
                        ],
                        borderColor: [
                            'rgba(255, 99, 132, 1)',
                            // Farben für weitere Balkenumrandungen
                        ],
                        borderWidth: 1
                    }]
                },
                options: {
                    scales: {
                        y: {
                            beginAtZero: true
                        }
                    }
                }
            });
        })
        .catch(error => console.error('Fehler beim Laden der Statistikdaten:', error));
});
