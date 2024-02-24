Use mangalib;

-- Inserts für Verlage
INSERT INTO verlage (name) VALUES ('altraverse');
INSERT INTO verlage (name) VALUES ('C LINES');
INSERT INTO verlage (name) VALUES ('Carlsen Manga');
INSERT INTO verlage (name) VALUES ('Chinabooks');
INSERT INTO verlage (name) VALUES ('CROCU');
INSERT INTO verlage (name) VALUES ('Crunchyroll');
INSERT INTO verlage (name) VALUES ('dani books');
INSERT INTO verlage (name) VALUES ('Dokico');
INSERT INTO verlage (name) VALUES ('Egmont Manga');
INSERT INTO verlage (name) VALUES ('Hayabusa');
INSERT INTO verlage (name) VALUES ('JNC Nina');
INSERT INTO verlage (name) VALUES ('KAZÉ Manga');
INSERT INTO verlage (name) VALUES ('Loewe Verlag');
INSERT INTO verlage (name) VALUES ('LYX');
INSERT INTO verlage (name) VALUES ('Manga Cult');
INSERT INTO verlage (name) VALUES ('Manga JAM Session');
INSERT INTO verlage (name) VALUES ('Manwha Cult');
INSERT INTO verlage (name) VALUES ('Manlin Verlag');
INSERT INTO verlage (name) VALUES ('Panini Manga');
INSERT INTO verlage (name) VALUES ('papertoons');
INSERT INTO verlage (name) VALUES ('Pixelite Novels');
INSERT INTO verlage (name) VALUES ('Planet Manga');
INSERT INTO verlage (name) VALUES ('Reprodukt');
INSERT INTO verlage (name) VALUES ('Schreiber & Leser');
INSERT INTO verlage (name) VALUES ('SKYLINE NOVELS');
INSERT INTO verlage (name) VALUES ('TOKYOPOP');
INSERT INTO verlage (name) VALUES ('VIZ Media');
INSERT INTO verlage (name) VALUES ('Yomeru');

-- Inserts für Status
INSERT INTO status (beschreibung) VALUES ('Vollständig');
INSERT INTO status (beschreibung) VALUES ('Aktuell');
INSERT INTO status (beschreibung) VALUES ('Unvollständig');
INSERT INTO status (beschreibung) VALUES ('Verkauft');

-- Inserts für Typen
INSERT INTO typen (bezeichnung) VALUES ('Manga');
INSERT INTO typen (bezeichnung) VALUES ('Light Novel');
INSERT INTO typen (bezeichnung) VALUES ('Webtoon');
INSERT INTO typen (bezeichnung) VALUES ('Artbook & Sonstiges');

-- Inserts für Formate
INSERT INTO formate (bezeichnung) VALUES ('Softcover');
INSERT INTO formate (bezeichnung) VALUES ('Hardcover');
INSERT INTO formate (bezeichnung) VALUES ('Digital');


INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (1, 2, 1, 3, 1, 'Solo Leveling', 9, 16.00, 144.00);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (2, 1, 3, 1, 1, 'Attack on Titan', 34, 6.95, 236.30);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (3, 2, 4, 1, 2, 'One Piece', 100, 5.95, 595.00);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (4, 1, 5, 1, 1, 'Naruto', 72, 6.50, 468.00);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (5, 3, 6, 1, 1, 'Death Note', 12, 6.95, 83.40);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (6, 2, 7, 1, 1, 'My Hero Academia', 30, 6.99, 209.70);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (7, 1, 8, 1, 1, 'Fullmetal Alchemist', 27, 6.95, 187.65);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (8, 3, 9, 1, 1, 'Dragon Ball', 42, 6.50, 273.00);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (9, 2, 10, 1, 1, 'Bleach', 74, 6.95, 514.30);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (10, 1, 11, 1, 1, 'Tokyo Ghoul', 14, 6.95, 97.30);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (11, 3, 12, 1, 1, 'Hunter x Hunter', 36, 6.95, 250.20);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (12, 2, 13, 1, 1, 'Demon Slayer', 23, 6.95, 159.85);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (13, 1, 14, 1, 1, 'Black Clover', 30, 6.99, 209.70);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (14, 3, 15, 1, 1, 'Jujutsu Kaisen', 18, 6.99, 125.82);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (15, 2, 16, 1, 1, 'Dr. Stone', 22, 6.95, 152.90);
INSERT INTO mangareihe (manga_index, status_id, verlag_id, typ_id, format_id, titel, anzahl_baende, preis_pro_band, gesamtpreis) VALUES (16, 1, 17, 1, 1, 'Vinland Saga', 25, 7.95, 198.75);

Use mangalib;
INSERT INTO sammelbaende (typ, multiplikator) VALUES ('2in1', 2);
INSERT INTO sammelbaende (typ, multiplikator) VALUES ('3in1', 3);
