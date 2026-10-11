// Tatsächliche Höhe einschließlich Zeilenumbrüchen/Zoom für Header, Seitenleiste und Dialoge reservieren.
(() => {
  const banner = document.querySelector(".manual-test-banner");
  if (!banner) return;
  const reserveSpace = () => {
    const height = banner.getBoundingClientRect().height;
    if (height > 0 && Number.isFinite(height)) {
      document.documentElement.style.setProperty("--manual-banner-height", `${height}px`);
    }
  };
  reserveSpace();
  if (typeof ResizeObserver !== "undefined") new ResizeObserver(reserveSpace).observe(banner);
  window.addEventListener("resize", reserveSpace);
})();
