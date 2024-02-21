package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.mangalib.entity.Typ;

@Repository
public interface TypRepository extends JpaRepository<Typ, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}

