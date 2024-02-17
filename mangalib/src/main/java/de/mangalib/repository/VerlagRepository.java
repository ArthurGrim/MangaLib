package de.mangalib.repository;

import de.mangalib.Verlage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerlagRepository extends JpaRepository<Verlage, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}
