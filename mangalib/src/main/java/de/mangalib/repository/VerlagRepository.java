package de.mangalib.repository;

import de.mangalib.Verlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerlagRepository extends JpaRepository<Verlag, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}
