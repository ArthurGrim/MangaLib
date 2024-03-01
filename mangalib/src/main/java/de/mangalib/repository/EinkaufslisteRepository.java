package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDate;

import de.mangalib.entity.EinkaufslisteItem;

@Repository
public interface EinkaufslisteRepository extends JpaRepository<EinkaufslisteItem, Long> {
    List<EinkaufslisteItem> findByErscheinungsdatumBetween(LocalDate start, LocalDate end);
}
