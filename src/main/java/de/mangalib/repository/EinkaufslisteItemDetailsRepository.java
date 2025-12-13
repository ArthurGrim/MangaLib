package de.mangalib.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.mangalib.entity.EinkaufslisteItemDetails;

@Repository
public interface EinkaufslisteItemDetailsRepository extends JpaRepository<EinkaufslisteItemDetails, Long> {
}
