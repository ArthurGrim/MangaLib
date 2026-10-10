# DOM-Regressionstests der Bandanzeige

Benötigt werden Node.js gemäß `package.json` (beispielsweise 24.15 oder neuer
innerhalb der 24er-Reihe) und pnpm 11.25.0. Java und die Gradle-Version der
Anwendung ändern sich dadurch nicht. jsdom ist ausschließlich eine
Entwicklungs-/Testabhängigkeit und wird nicht in das Spring-Boot-Artefakt gepackt.

Aus dem Projektverzeichnis:

```powershell
pnpm install --frozen-lockfile --ignore-scripts
pnpm run test:dom
```

Die Installation lädt npm-Pakete; Installationsskripte werden nicht ausgeführt.
Nach der Installation ist auch `node --test src/test/js/home.test.cjs` möglich.
Die Tests selbst benötigen weder Datenbank noch Netzwerk oder echten Browser.
HTTP-Antworten, neue Fenster und IntersectionObserver werden gezielt ersetzt;
externe Ressourcen, XMLHttpRequest und WebSocket sind gesperrt.

Die Tests führen das tatsächliche `home.js` in einem jsdom-DOM aus und prüfen
Textknoten, Attribute, DOM-Aufbau und Klickereignisse. Inline- und importierte
Seitenskripte sind deaktiviert. Sie sind keine vollständige Browser-/UI-Abnahme:
Bilddarstellung, Layout, echtes Scrollen und Popup-Verhalten müssen separat in
einer freigegebenen Umgebung geprüft werden. Keine Selenium-/Scraperstarts.

Diese Suite läuft separat von Gradle. Für sämtliche Java-/Datenbanktests gelten
weiterhin unverändert die Schutzregeln aus `TESTING.md` im Projektverzeichnis.
