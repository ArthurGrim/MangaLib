-- S03_001: additiv, ausschließlich mangalib_test.
-- Vorbereitet nach lesender Vorprüfung am 15.11.2026: MySQL 8.0.36, baende.id BIGINT (signed).
-- NICHT automatisch ausführen. Separate Schemafreigabe und Admin-Sitzung erforderlich.
-- Vorher aktuellen Snapshot/Fingerabdruck prüfen; keine aktive schreibende Anwendung.
-- Keine Wiederholung, kein IF NOT EXISTS, keine Initialisierung alter Zähler.
CREATE TABLE `mangalib_test`.`band_reading_events` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    band_id BIGINT NOT NULL,
    completion_date DATE NOT NULL,
    initial_completion_date DATE NOT NULL,
    kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    volume_factor INT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reading_operation_band (operation_id, band_id),
    KEY ix_reading_month_kind (completion_date, kind),
    KEY ix_reading_band_date (band_id, completion_date, id),
    CONSTRAINT fk_reading_event_band FOREIGN KEY (band_id)
        REFERENCES `mangalib_test`.`baende` (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
