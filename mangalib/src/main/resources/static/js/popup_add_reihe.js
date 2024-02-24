document.addEventListener("DOMContentLoaded", function () {
  console.log("Skript geladen");

  // Referenzen auf Elemente
  const popupContainer = document.getElementById("popupContainer");
  const addButton = document.getElementById("addButton"); // Geändert zu getElementById
  const cancelButton = document.querySelector(".cancel-button");
  const saveButton = document.querySelector(".save-button");

  // Event-Handler, um das Pop-up anzuzeigen
  addButton.addEventListener("click", function () {
    console.log("Add-Button geklickt");
    popupContainer.style.display = "flex";
  });

  // Event-Handler, um das Pop-up zu verbergen
  cancelButton.addEventListener("click", function () {
    popupContainer.style.display = "none";
  });

  // Event-Handler für den Speichern-Button
  saveButton.addEventListener("click", function (event) {
    event.preventDefault(); // Verhindert das Standardverhalten des Formulars
    const form = document.querySelector("#popupForm form"); // Ersetzen Sie dies durch die tatsächliche ID Ihres Formulars
    // Validierung und Datensammlung hier
    form.submit(); // Sendet das Formular
  });

  // Weitere Event-Handler und Logik nach Bedarf...
});
