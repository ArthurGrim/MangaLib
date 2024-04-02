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

-- Inserts für Sammelbaende
INSERT INTO sammelbaende (typ, multiplikator) VALUES ('2in1', 2);
INSERT INTO sammelbaende (typ, multiplikator) VALUES ('3in1', 3);
INSERT INTO sammelbaende (typ, multiplikator) VALUES ('4in1', 4);
INSERT INTO sammelbaende (typ, multiplikator) VALUES ('5in1', 5);

