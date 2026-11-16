-- S03: separater Admin-Schritt nach Anlage der manuellen Ereignistabelle.
-- Tatsächlicher Laufzeitaccount am 16.11.2026 lesend bestätigt.
-- Nur die neue Tabelle, keine Wildcards, kein DELETE/DDL/GRANT OPTION.
GRANT SELECT, INSERT, UPDATE
ON `mangalib_manual_test`.`band_reading_events`
TO 'mangalib_manual_test_user'@'127.0.0.1';
