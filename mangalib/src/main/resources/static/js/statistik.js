document.addEventListener("DOMContentLoaded", function () {
    console.log("DOMContentLoaded event fired");
  
    // Erstes Diagramm: Bar Chart für Bandkategorien
    fetch("/statistik/bandKategorien")
      .then((response) => response.json())
      .then((data) => {
        console.log("Bandkategorien-Daten erhalten:", data);
        
        const reihenKategorien = [
          "1 Band",
          "2-5 Bände",
          "6-10 Bände",
          "11-20 Bände",
          "21-50 Bände",
          "51-100 Bände",
          "101+ Bände",
        ];
  
        const werte = [
          data.reihenMitEinemBand,
          data.reihenMitZweiBisFuenfBaenden,
          data.reihenMitSechsBisZehnBaenden,
          data.reihenMitElfBisZwanzigBaenden,
          data.reihenMitEinundzwanzigBisFuenfzigBaenden,
          data.reihenMitEinundfuenfzigBisHundertBaenden,
          data.reihenMitMehrAlsHundertBaenden,
        ];
  
        const barChartWrap = document.querySelector(".bar-chart-wrap");
        const xAxis = document.querySelector(".x-axis");
  
        reihenKategorien.forEach((kategorie, index) => {
          const wert = werte[index];
  
          // Bar-Element erstellen
          const barWrap = document.createElement("div");
          barWrap.className = "bar-wrap";
          barWrap.style.width = "140px";
  
          const bar = document.createElement("div");
          bar.className = "bar";
          bar.style.height = `${wert}%`;
  
          const valueDiv = document.createElement("div");
          valueDiv.className = "value";
          valueDiv.textContent = wert;
  
          bar.appendChild(valueDiv);
          barWrap.appendChild(bar);
          barChartWrap.appendChild(barWrap);
  
          // Label für die X-Achse erstellen
          const label = document.createElement("div");
          label.className = "bar-label";
          label.style.width = "140px";
          label.textContent = kategorie;
          xAxis.appendChild(label);
        });
      })
      .catch((error) => console.error("Fehler beim Laden der Daten:", error));
  
    // Zweites Diagramm: Liniendiagramm für Monatsdaten
    const yearSelector = document.getElementById("yearSelector");
    const optionBands = document.getElementById("optionBands");
    const optionSpending = document.getElementById("optionSpending");
  
    let currentYear = new Date().getFullYear();
    let currentType = "baende";
  
    populateYearSelector();
    console.log("Loading chart data initially");
    loadChartData(currentYear, currentType);
  
    yearSelector.addEventListener("change", function () {
      currentYear = yearSelector.value;
      console.log("Year changed, loading chart data");
      loadChartData(currentYear, currentType);
    }, { once: true });
  
    optionBands.addEventListener("click", function () {
      currentType = "baende";
      setActiveOption(optionBands, optionSpending);
      console.log("Option switched to bands, loading chart data");
      loadChartData(currentYear, currentType);
    }, { once: true });
  
    optionSpending.addEventListener("click", function () {
      currentType = "geld";
      setActiveOption(optionSpending, optionBands);
      console.log("Option switched to spending, loading chart data");
      loadChartData(currentYear, currentType);
    }, { once: true });
  
    function setActiveOption(active, inactive) {
      active.classList.add("active");
      inactive.classList.remove("active");
    }
  
    function populateYearSelector() {
      const currentYear = new Date().getFullYear();
      for (let i = currentYear; i >= 2000; i--) {
        let option = document.createElement("option");
        option.value = i;
        option.text = i;
        yearSelector.appendChild(option);
      }
      yearSelector.value = currentYear; // Setze das aktuelle Jahr als ausgewähltes Jahr
    }
  
    function loadChartData(year, type) {
      console.log(`Fetching data for year: ${year}, type: ${type}`);
      fetch(`/statistik/monatsdaten?jahr=${year}&typ=${type}`)
        .then((response) => response.json())
        .then((data) => {
          console.log("Monatsdaten erhalten:", data);
          renderChart(data, type === "geld"); // Übergebe true für Geld ausgegeben
        })
        .catch((error) => console.error("Fehler beim Laden der Daten:", error));
    }
  
    function renderChart(data, isMoney) {
      const lineChart = document.querySelector(".line-chart");
      const xAxis = document.querySelector(".x-axis-pro-monat");
      lineChart.innerHTML = "";
      xAxis.innerHTML = "";
  
      const fullYearData = {};
      for (let i = 1; i <= 12; i++) {
        fullYearData[i] = 0; // Standardwert 0 für jeden Monat
      }
  
      Object.keys(data).forEach((month) => {
        fullYearData[month] = parseFloat(data[month]); // Sicherstellen, dass der Wert numerisch ist
      });
  
      const maxYValue = Math.max(...Object.values(fullYearData)) || 1;
      const chartWidth = lineChart.clientWidth;
      const paddingBottom = 20; // Abstand nach unten
      const chartHeight = 300 - paddingBottom;
  
      lineChart.setAttribute('viewBox', `0 0 ${chartWidth} ${chartHeight + paddingBottom}`);
      lineChart.setAttribute('preserveAspectRatio', 'none');
  
      Object.keys(fullYearData).forEach((month, index) => {
        const totalMonths = 12;
  
        // Breite des Containers für jedes Label
        const labelWidth = chartWidth / totalMonths;
        // Berechne die Mitte des Labels als Position für den Punkt
        const cx = labelWidth * (index + 0.5);
        const cy = isFinite(fullYearData[month])
          ? chartHeight - (fullYearData[month] / maxYValue) * (chartHeight - 50)
          : NaN;
  
        if (!isNaN(cy)) {
          let circle = document.createElementNS(
            "http://www.w3.org/2000/svg",
            "circle"
          );
          circle.setAttribute("cx", cx);
          circle.setAttribute("cy", cy);
          circle.setAttribute("r", 10); // Doppelte Punktgröße
          circle.classList.add("point");
          lineChart.appendChild(circle);
  
          let valueLabel = document.createElementNS(
            "http://www.w3.org/2000/svg",
            "text"
          );
          valueLabel.setAttribute("x", cx);
          valueLabel.setAttribute("y", cy - 15);
          valueLabel.classList.add("value-label");
          valueLabel.textContent = isMoney
            ? `${fullYearData[month].toFixed(2)} €`
            : fullYearData[month];
          valueLabel.style.fontSize = "14px"; // Schriftgröße ändern
          valueLabel.style.fill = "#FFFFFF"; // Schriftfarbe ändern
          lineChart.appendChild(valueLabel);
        }
  
        let labelDiv = document.createElement("div");
        labelDiv.classList.add("x-axis-label");
        labelDiv.style.width = `${100 / totalMonths}%`;
        labelDiv.innerText = new Date(0, month - 1).toLocaleString("de-DE", {
          month: "short",
        });
        xAxis.appendChild(labelDiv);
      });
  
      drawLine(fullYearData, maxYValue);
    }
  
    function drawLine(data, maxYValue) {
      const points = [...document.querySelectorAll(".point")];
      if (points.length === 0) return;
  
      let d = `M ${points[0].getAttribute("cx")} ${points[0].getAttribute("cy")}`;
      for (let i = 1; i < points.length; i++) {
        d += ` L ${points[i].getAttribute("cx")} ${points[i].getAttribute("cy")}`;
      }
  
      let path = document.createElementNS("http://www.w3.org/2000/svg", "path");
      path.setAttribute("d", d);
      path.setAttribute("fill", "none");
      path.setAttribute("stroke", "#274272"); // Farbe für die Linie
      path.setAttribute("stroke-width", "2");
      path.setAttribute("vector-effect", "non-scaling-stroke"); // Verhindert das Skalieren der Linie bei SVG-Skalierung
      document.querySelector(".line-chart").appendChild(path);
    }
  
    // Initial chart load
    const initialYear = yearSelector.value;
    console.log("Loading chart data initially");
    loadChartData(initialYear, currentType); // Lädt die initiale Grafik
  });
  