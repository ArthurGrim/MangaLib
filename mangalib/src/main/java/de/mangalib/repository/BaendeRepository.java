package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import de.mangalib.entity.Baende;

public interface BaendeRepository extends JpaRepository<Baende, Long> {
    // Weitere benutzerdefinierte Abfragen nach Bedarf
}
