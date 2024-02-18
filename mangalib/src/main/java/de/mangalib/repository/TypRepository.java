package de.mangalib.repository;

import de.mangalib.Typ;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TypRepository extends JpaRepository<Typ, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}

