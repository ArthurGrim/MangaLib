package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import de.mangalib.entity.Band;

public interface BandRepository extends JpaRepository<Band, Long> {
    // Weitere benutzerdefinierte Abfragen nach Bedarf
}
