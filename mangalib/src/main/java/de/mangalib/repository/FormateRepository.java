package de.mangalib.repository;

import de.mangalib.Formate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FormateRepository extends JpaRepository<Formate, Long> {
    // Benutzerdefinierte Methoden nach Bedarf
}
