package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import de.mangalib.entity.Sammelband;

@Repository
public interface SammelbandRepository extends JpaRepository<Sammelband, Long> {
    // Hier können Sie bei Bedarf benutzerdefinierte Abfragemethoden hinzufügen
}
