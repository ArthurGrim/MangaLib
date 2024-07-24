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

-- Tabelle für Sammelbandarten
CREATE TABLE sammelbaende (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    multiplikator INT NOT NULL,
    typ VARCHAR(255) NOT NULL
);

-- Tabelle für die MangaReihen
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
    gesamtpreis DECIMAL(10, 2),
    aenderung_gesamtpreis DECIMAL(10 ,2),
    erstellt_am DATE DEFAULT (CURRENT_DATE),
    aktualisiert_am DATE DEFAULT (CURRENT_DATE),
    ist_ebay_preis BOOLEAN DEFAULT FALSE,
    ist_vergriffen BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (status_id) REFERENCES status(status_id),
    FOREIGN KEY (verlag_id) REFERENCES verlage(verlag_id),
    FOREIGN KEY (typ_id) REFERENCES typen(typ_id),
    FOREIGN KEY (format_id) REFERENCES formate(format_id)
);

CREATE TABLE mangadetails (
    id BIGINT NOT NULL,
    status_de VARCHAR(255),
    anzahl_baende_de INT,
    status_erstv VARCHAR(255),
    herkunft VARCHAR(255),
    start_jahr INT,
    anzahl_baende_erstv INT,
    sammelbaende_id BIGINT,
    anilist_url VARCHAR(255),
    cover_url VARCHAR(255),
    ist_gelesen BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (id),
    FOREIGN KEY (id) REFERENCES mangareihe(id),
    FOREIGN KEY (sammelbaende_id) REFERENCES sammelbaende(id)
);

CREATE TABLE baende (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    manga_reihe_id BIGINT,
    band_nr INT,
    preis DECIMAL(10, 2),
    bild_url VARCHAR(255) DEFAULT 'https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/No-Image-Placeholder.svg/330px-No-Image-Placeholder.svg.png?20200912122019',
    mp_url VARCHAR(255),
    ist_special BOOLEAN,
    aenderung_preis DECIMAL(10 ,2),
    erstellt_am DATE DEFAULT (CURRENT_DATE),
    aktualisiert_am DATE DEFAULT (CURRENT_DATE),
    ist_gelesen BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (manga_reihe_id) REFERENCES mangareihe(id)
);

CREATE TABLE einkaufsliste (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    manga_index INT,
    verlag_id BIGINT,
    typ_id BIGINT,
    format_id BIGINT,
    titel VARCHAR (255),
    anzahl_baende INT,
    preis DECIMAL(10, 2),
    gesamtpreis DECIMAL(10, 2),
    aenderung_gesamtpreis DECIMAL(10 ,2),
    erscheinungsdatum DATE,
    ist_ebay_preis BOOLEAN,
    ist_vergriffen BOOLEAN,
    gekauft BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (verlag_id) REFERENCES Verlage(verlag_id),
    FOREIGN KEY (typ_id) REFERENCES Typen(typ_id),
    FOREIGN KEY (format_id) REFERENCES Formate(format_id)
);

CREATE TABLE einkaufslistedetails (
    id BIGINT NOT NULL PRIMARY KEY,
    status_de VARCHAR(255),
    anzahl_baende_de INT,
    status_erstv VARCHAR(255),
    herkunft VARCHAR(255),
    start_jahr INT,
    anzahl_baende_erstv INT,
    anilist_url VARCHAR(255),
    cover_url VARCHAR(255),
    sammelbaende_id BIGINT,
    FOREIGN KEY (id) REFERENCES einkaufsliste(id),
    FOREIGN KEY (sammelbaende_id) REFERENCES sammelbaende(id)
);
