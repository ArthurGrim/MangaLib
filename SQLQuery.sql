-- Tabelle für Verlage
CREATE TABLE Verlage (
    verlagID INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Status
CREATE TABLE Status (
    statusID INT AUTO_INCREMENT PRIMARY KEY,
    beschreibung VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Typen
CREATE TABLE Typen (
    typID INT AUTO_INCREMENT PRIMARY KEY,
    bezeichnung VARCHAR(255) UNIQUE NOT NULL
);

-- Tabelle für Formate
CREATE TABLE Formate (
    formatID INT AUTO_INCREMENT PRIMARY KEY,
    bezeichnung VARCHAR(255) UNIQUE NOT NULL
);

-- Angepasste Tabelle MangaReihe mit zusätzlichen boolischen Feldern
CREATE TABLE MangaReihe (
    id INT AUTO_INCREMENT PRIMARY KEY,
    mangaIndex INT,
    statusID INT,
    verlagID INT,
    typID INT,
    formatID INT,
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