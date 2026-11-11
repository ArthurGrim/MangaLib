document.addEventListener("DOMContentLoaded", () => {
  renderDistributions("/statistik/verteilungen", "distributions-grid", "distributions-status", false);
  if (document.getElementById("statistics-details-grid")) {
    renderDistributions("/statistik/weitere", "statistics-details-grid", "statistics-details-status", true);
  }
});

async function renderDistributions(endpoint, gridId, statusId, ranked) {
  const grid = document.getElementById(gridId);
  const status = document.getElementById(statusId);
  const colors = ["#537fca", "#bb739e", "#47a696", "#d39a46", "#8b83bd", "#bf7365"];
  try {
    const response = await fetch(endpoint);
    if (!response.ok) throw new Error("Verteilungen nicht verfügbar");
    const data = await response.json();
    if (!Array.isArray(data)) throw new Error("Ungültige Verteilungen");
    const cards = data.map(distribution => {
      if (!distribution || typeof distribution.title !== "string" ||
          typeof distribution.unit !== "string" || !distribution.counts ||
          typeof distribution.counts !== "object" || Array.isArray(distribution.counts)) {
        throw new Error("Ungültige Verteilung");
      }
      const entries = Object.entries(distribution.counts);
      if (ranked) entries.sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "de"));
      let total = 0;
      for (const [, count] of entries) {
        if (!Number.isSafeInteger(count) || count < 0) throw new Error("Ungültige Anzahl");
        total += count;
      }
      if (!Number.isSafeInteger(total)) throw new Error("Ungültige Summe");
      const card = document.createElement("article");
      card.className = "distribution-card";
      const heading = document.createElement("h3");
      heading.textContent = distribution.title;
      const summary = document.createElement("p");
      summary.textContent = total.toLocaleString("de-DE") + " " + distribution.unit;
      const pie = document.createElement("div");
      pie.className = "distribution-pie";
      pie.setAttribute("aria-hidden", "true");
      const list = document.createElement("ul");
      let position = 0;
      const segments = [];
      entries.forEach(([label, count], index) => {
        const color = colors[index % colors.length];
        const next = total ? position + count / total * 100 : position;
        segments.push(color + " " + position + "% " + next + "%");
        position = next;
        const item = document.createElement("li");
        const swatch = document.createElement("span");
        swatch.className = "distribution-swatch";
        swatch.style.backgroundColor = color;
        const text = document.createElement("span");
        const percent = total ? count / total * 100 : 0;
        text.textContent = label + ": " + count.toLocaleString("de-DE") + " (" +
          percent.toLocaleString("de-DE", { maximumFractionDigits: 1 }) + " %)";
        if (ranked) {
          const meter = document.createElement("meter");
          meter.min = 0;
          meter.max = total || 1;
          meter.value = count;
          meter.setAttribute("aria-label", label);
          item.append(meter);
        }
        item.append(swatch, text);
        list.append(item);
      });
      if (total) pie.style.backgroundImage = "conic-gradient(" + segments.join(",") + ")";
      else summary.textContent = "Keine " + distribution.unit + " vorhanden";
      card.append(heading, summary);
      if (!ranked) card.append(pie);
      card.append(list);
      return card;
    });
    grid.replaceChildren(...cards);
    status.textContent = "";
  } catch {
    grid.replaceChildren();
    status.textContent = "Verteilungen konnten nicht geladen werden. Bitte laden Sie die Seite erneut.";
  }
}
