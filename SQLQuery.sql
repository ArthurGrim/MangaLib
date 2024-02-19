Use mangalib;
-- Tabelle für Verlage
CREATE TABLE verlage (
    verlagID BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Status
CREATE TABLE status (
    statusID BIGINT AUTO_INCREMENT PRIMARY KEY,
    beschreibung VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Typen
CREATE TABLE typen (
    typID BIGINT AUTO_INCREMENT PRIMARY KEY,
    bezeichnung VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Formate
CREATE TABLE formate (
    formatID BIGINT AUTO_INCREMENT PRIMARY KEY,
    bezeichnung VARCHAR(255) UNIQUE NOT NULL
);

-- Angepasste Tabelle MangaReihe mit zusätzlichen boolischen Feldern
CREATE TABLE mangareihe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mangaIndex INT,
    statusID BIGINT,
    verlagID BIGINT,
    typID BIGINT,
    formatID BIGINT,
    titel VARCHAR(255) NOT NULL,
    anzahlBaende INT,
    preisProBand DOUBLE,
    gesamtpreis DOUBLE,
    erstelltAm TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    aktualisiertAm TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    istEbayPreis BOOLEAN DEFAULT FALSE,
    istVergriffen BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (statusID) REFERENCES Status(statusID),
    FOREIGN KEY (verlagID) REFERENCES Verlage(verlagID),
    FOREIGN KEY (typID) REFERENCES Typen(typID),
    FOREIGN KEY (formatID) REFERENCES Formate(formatID)
);
