# DOM-Regressionstests der Bandanzeige und lokalen Zugriffssicherheit

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
Nach der Installation ist auch `node --test src/test/js/*.test.cjs` möglich.
Die Tests selbst benötigen weder Datenbank noch Netzwerk oder echten Browser.
HTTP-Antworten, neue Fenster und IntersectionObserver werden gezielt ersetzt;
externe Ressourcen, XMLHttpRequest und WebSocket sind gesperrt.

Die Tests führen das tatsächliche `home.js` in einem jsdom-DOM aus und prüfen
Textknoten, Attribute, DOM-Aufbau und Klickereignisse. Inline- und importierte
Seitenskripte sind deaktiviert. Sie sind keine vollständige Browser-/UI-Abnahme:
Bilddarstellung, Layout, echtes Scrollen und Popup-Verhalten müssen separat in
einer freigegebenen Umgebung geprüft werden. Keine Selenium-/Scraperstarts.

`security.test.cjs` prüft zusätzlich die gemeinsame CSRF-Fetch-Hilfe und führt
die tatsächlichen Dialog-/Einkaufslistenskripte mit den Formular-DOMs der Templates
aus. Tokenheader, unveränderte Bodies, Autofill-POST/Datenübernahme, Speichern,
Löschen und Kauf mit Statusauswahl werden mit Fetch-Spies geprüft. Fremde Origins
einschließlich anderer lokaler Adressen/Ports erhalten keinen Token; Redirects
sind gesperrt. Fehlende Tokens und 403 führen zum Hinweis auf manuelles Neuladen,
niemals zu automatischen Schreibwiederholungen. Stylesheet-Links werden nur in
den Testfixtures entfernt; Netzwerk, echte Browser und importierte Seitenskripte
bleiben gesperrt. Die zusätzliche Quelltextinventur der Schreibaufrufe ist kein
Ersatz für diese ausgeführten DOM-/Ereignisprüfungen.

Diese Suite läuft separat von Gradle. Für sämtliche Java-/Datenbanktests gelten
weiterhin unverändert die Schutzregeln aus `TESTING.md` im Projektverzeichnis.

`manual-acceptance.test.cjs` ergänzt den echten Bearbeitungsdialog um NULL-/Boolean-
und URL-Felderhalt sowie kontrollierte Speicherfehler (400/Text, 403, falsches JSON-
Format und Netzwerkfehler), ohne Replay. Die Banner-Tests prüfen DOM, CSS-Regeln
und Höhenanpassung mit synthetischer Geometrie; jsdom berechnet kein echtes Layout.
Vier weitere Fälle in `security.test.cjs` sichern unveränderte Autofill-Felder bei
400/JSON-Ablehnung auf allen bestehenden Reihen-/Band-Dialogpfaden ab.
Die Auswahl der Banner-Assets im normalen/manuellen Modell wird zusätzlich durch
`manualSafetyTest` mit Thymeleaf geprüft. Überdeckung, Zoom und Dialogpositionen
muss der Eigentümer im echten Browser nachprüfen.

`write-feedback.test.cjs` ergänzt 65 datenbankfreie Fälle für reale Schreibdialoge,
Status-/Format-/Netzwerkfehler, sichere Inline-Meldungen, Werterhalt und Klicksperren
sowie Kauf-417/Cancel, lokale manuelle Cover und Lazy Loading. Stand 11.10.2026:
158 DOM-Tests bestanden. Response-Doubles ersetzen HTTP; jsdom führt weder echte
Navigation/Reloads noch eine Browserabnahme aus. Details und Grenzen: TESTING.md
und MANUAL_TESTING.md, Abschnitt 12. Keine neue Dependency notwendig.
