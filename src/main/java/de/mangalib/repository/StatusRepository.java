package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.mangalib.entity.Status;

@Repository
public interface StatusRepository extends JpaRepository<Status, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}
