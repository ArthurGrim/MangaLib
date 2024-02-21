package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.mangalib.entity.Verlag;

@Repository
public interface VerlagRepository extends JpaRepository<Verlag, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}
