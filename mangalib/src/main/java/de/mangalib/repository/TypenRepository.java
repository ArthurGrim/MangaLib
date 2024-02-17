package de.mangalib.repository;

import de.mangalib.Typen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TypenRepository extends JpaRepository<Typen, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}

