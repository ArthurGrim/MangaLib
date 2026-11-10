document.addEventListener("DOMContentLoaded", () => {
  const toggle = document.getElementById("reading-time-rereads");
  const value = document.getElementById("reading-time-value");
  toggle.addEventListener("change", () => {
    value.textContent = toggle.checked ? value.dataset.repeated : value.dataset.first;
  });
});
