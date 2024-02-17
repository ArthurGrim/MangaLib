package de.mangalib.repository;

import de.mangalib.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StatusRepository extends JpaRepository<Status, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}
