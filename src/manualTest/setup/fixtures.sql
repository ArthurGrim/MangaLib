-- EINMALIG nach Schemaimport, nur in einer vollständig leeren manuellen Datenbank.
-- Vollständig qualifizierte Namen, feste IDs: erneute Ausführung scheitert statt zu überschreiben.
-- mysql im Batchmodus ohne --force verwenden. Kein automatischer Anwendungsimport.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO mangalib_manual_test.verlage (verlag_id, name) VALUES (1, 'Testverlag');
INSERT INTO mangalib_manual_test.status (status_id, beschreibung) VALUES
 (1, 'Vollständig'), (2, 'Aktuell'), (3, 'Unvollständig'), (4, 'Verkauft');
INSERT INTO mangalib_manual_test.typen (typ_id, bezeichnung) VALUES
 (1, 'Manga'), (2, 'Light Novel'), (3, 'Webtoon'), (4, 'Artbook & Sonstiges');
INSERT INTO mangalib_manual_test.formate (format_id, bezeichnung) VALUES
 (1, 'Softcover'), (2, 'Hardcover'), (3, 'Digital');
INSERT INTO mangalib_manual_test.sammelbaende (id, multiplikator, typ) VALUES
 (1, 1, '1in1'), (2, 2, '2in1'), (3, 3, '3in1'), (4, 4, '4in1'), (5, 5, '5in1');

INSERT INTO mangalib_manual_test.mangareihe
 (id, manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende,
 preis_pro_band, gesamtpreis, aenderung_gesamtpreis, erstellt_am, aktualisiert_am, ist_ebay_preis, ist_vergriffen)
 VALUES
 (1, 900001, 2, 1, 1, 1, 'MANUELL ÄÖÜ & <img src=x onerror="window.__manualXss=1">', 2, 6.99, 13.98, 0.00, '2024-12-31', '2024-12-31 12:00:00', false, false),
 (2, 900002, 1, 1, 2, 2, 'MANUELL O''Brien "Test"', 2, 7.49, 14.98, 0.00, '2025-01-01', '2025-01-01 12:00:00', false, NULL);
INSERT INTO mangalib_manual_test.mangadetails
 (id, status_de, anzahl_baende_de, status_erstv, herkunft, start_jahr, anzahl_baende_erstv,
 sammelbaende_id, anilist_url, cover_url, ist_gelesen, reread)
 VALUES
 (1, 'Laufend', 2, 'Laufend', 'Japan', 2024, 2, 1, 'http://localhost:18080/home', 'http://localhost:18080/manual-test/cover.svg', false, 0),
 (2, 'Abgeschlossen', 2, 'Abgeschlossen', 'Japan', 2025, 2, 1, NULL, 'http://localhost:18080/manual-test/cover.svg', true, 1);
INSERT INTO mangalib_manual_test.baende
 (id, manga_reihe_id, band_nr, band_index, preis, bild_url, mp_url, ist_special, aenderung_preis, erstellt_am, aktualisiert_am, ist_gelesen, reread)
 VALUES
 (1, 1, 1, 900001, 6.99, 'http://localhost:18080/manual-test/cover.svg?quote=%27%22', 'http://localhost:18080/home?band=1', NULL, 0.00, '2024-12-31', '2024-12-31', true, 0),
 (2, 1, 2, 900002, 6.99, 'http://localhost:18080/manual-test/cover.svg', 'http://localhost:18080/home?band=2', false, 0.00, '2025-01-01', '2025-01-01', false, 0),
 (3, 2, 1, 900001, 7.49, 'http://localhost:18080/manual-test/cover.svg', 'http://localhost:18080/home?band=3', true, 0.00, '2025-01-01', '2025-01-01', true, 1),
 (4, 2, 2, 900002, 7.49, 'http://localhost:18080/manual-test/cover.svg', 'http://localhost:18080/home?band=4', false, 0.00, '2025-01-01', '2025-01-01', true, 1);
INSERT INTO mangalib_manual_test.einkaufsliste
 (id, manga_index, verlag_id, typ_id, format_id, titel, anzahl_baende, preis, gesamtpreis,
 aenderung_gesamtpreis, erscheinungsdatum, ist_ebay_preis, ist_vergriffen, gekauft)
 VALUES
 (1, 900001, 1, 1, 1, 'MANUELL Kauf bestehende Reihe', 1, 6.99, 6.99, 0.00, '2024-12-31', false, false, false),
 (2, 900003, 1, 1, 1, 'MANUELL Kauf neue Reihe', 1, 7.49, 7.49, 0.00, '2025-01-01', NULL, false, false),
 (3, 900004, 1, 1, 1, 'MANUELL Löschen', 1, 5.99, 5.99, 0.00, '2025-01-02', false, false, false);
INSERT INTO mangalib_manual_test.einkaufslistedetails
 (id, status_de, anzahl_baende_de, status_erstv, herkunft, start_jahr, anzahl_baende_erstv, sammelbaende_id, anilist_url, cover_url)
 VALUES
 (1, 'Laufend', 3, 'Laufend', 'Japan', 2024, 3, 1, NULL, 'http://localhost:18080/manual-test/cover.svg'),
 (2, 'Laufend', 1, 'Laufend', 'Japan', 2025, 1, 1, NULL, 'http://localhost:18080/manual-test/cover.svg'),
 (3, 'Laufend', 1, 'Laufend', 'Japan', 2025, 1, 1, NULL, 'http://localhost:18080/manual-test/cover.svg');
COMMIT;
