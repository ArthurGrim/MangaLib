package de.mangalib.repository;

import de.mangalib.Format;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FormatRepository extends JpaRepository<Format, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}
