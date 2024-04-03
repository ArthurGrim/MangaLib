document.addEventListener("DOMContentLoaded", function() {
    fetch('/api/statistik/bandKategorien')
        .then(response => response.json())
        .then(data => {
            const reihenKategorien = [
                '1 Band',
                '2-5 Bände',
                '6-10 Bände',
                '11-20 Bände',
                '21-50 Bände',
                '51-100 Bände',
                '101+ Bände'
            ];

            const werte = [
                data.reihenMitEinemBand,
                data.reihenMitZweiBisFuenfBaenden,
                data.reihenMitSechsBisZehnBaenden,
                data.reihenMitElfBisZwanzigBaenden,
                data.reihenMitEinundzwanzigBisFuenfzigBaenden,
                data.reihenMitEinundfuenfzigBisHundertBaenden,
                data.reihenMitMehrAlsHundertBaenden
            ];

            const barChartWrap = document.querySelector('.bar-chart-wrap');
            const xAxis = document.querySelector('.x-axis');

            reihenKategorien.forEach((kategorie, index) => {
                const wert = werte[index];

                // Bar-Element erstellen
                const barWrap = document.createElement('div');
                barWrap.className = 'bar-wrap';
                barWrap.style.width = '140px';

                const bar = document.createElement('div');
                bar.className = 'bar';
                bar.style.height = `${wert}%`;

                const valueDiv = document.createElement('div');
                valueDiv.className = 'value';
                valueDiv.textContent = wert;

                bar.appendChild(valueDiv);
                barWrap.appendChild(bar);
                barChartWrap.appendChild(barWrap);

                // Label für die X-Achse erstellen
                const label = document.createElement('div');
                label.className = 'bar-label';
                label.style.width = '140px';
                label.textContent = kategorie;
                xAxis.appendChild(label);
            });
        })
        .catch(error => console.error('Fehler beim Laden der Daten:', error));
});
