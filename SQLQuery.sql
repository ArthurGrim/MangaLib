USE mangalib;

-- Tabelle für Verlage
CREATE TABLE verlage (
    verlag_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Status
CREATE TABLE status (
    status_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    beschreibung VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Typen
CREATE TABLE typen (
    typ_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bezeichnung VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Formate
CREATE TABLE formate (
    format_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bezeichnung VARCHAR(255) UNIQUE NOT NULL
);

-- Angepasste Tabelle MangaReihe mit zusätzlichen boolischen Feldern
CREATE TABLE mangareihe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    manga_index INT,
    status_id BIGINT,
    verlag_id BIGINT,
    typ_id BIGINT,
    format_id BIGINT,
    titel VARCHAR(255) NOT NULL,
    anzahl_baende INT,
    preis_pro_band DOUBLE,
    gesamtpreis DOUBLE,
    erstellt_am TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    aktualisiert_am TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ist_ebay_preis BOOLEAN DEFAULT FALSE,
    ist_vergriffen BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (status_id) REFERENCES status(status_id),
    FOREIGN KEY (verlag_id) REFERENCES verlage(verlag_id),
    FOREIGN KEY (typ_id) REFERENCES typen(typ_id),
    FOREIGN KEY (format_id) REFERENCES formate(format_id)
);
