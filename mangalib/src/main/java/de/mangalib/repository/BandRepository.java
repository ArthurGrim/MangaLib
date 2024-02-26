package de.mangalib.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.mangalib.entity.Band;

public interface BandRepository extends JpaRepository<Band, Long> {
    @Query("SELECT b FROM Band b WHERE b.mangaReihe.id = :mangaReiheId AND b.bandNr = 1")
    Optional<Band> findFirstBandByMangaReiheId(@Param("mangaReiheId") Long mangaReiheId);

}
