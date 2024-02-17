package de.mangalib.repository;

import de.mangalib.MangaReihe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MangaReiheRepository extends JpaRepository<MangaReihe, Long> {
    // Hier können bei Bedarf benutzerdefinierte Methoden hinzugefügt werden
}
