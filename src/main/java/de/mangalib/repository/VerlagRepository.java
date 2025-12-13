package de.mangalib.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.mangalib.entity.Verlag;

@Repository
public interface VerlagRepository extends JpaRepository<Verlag, Long> {

    Optional<Verlag> findByName(String name);
    // Benutzerdefinierte Methoden nach Bedarf
}
