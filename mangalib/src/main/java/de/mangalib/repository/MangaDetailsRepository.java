package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import de.mangalib.entity.MangaDetails;

@Repository
public interface MangaDetailsRepository extends JpaRepository<MangaDetails, Long> {
    // Hier können Sie bei Bedarf benutzerdefinierte Abfragemethoden hinzufügen
}