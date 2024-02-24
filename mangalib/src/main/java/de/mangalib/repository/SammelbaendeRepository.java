package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import de.mangalib.entity.Sammelbaende;

@Repository
public interface SammelbaendeRepository extends JpaRepository<Sammelbaende, Long> {
    // Hier können Sie bei Bedarf benutzerdefinierte Abfragemethoden hinzufügen
}
