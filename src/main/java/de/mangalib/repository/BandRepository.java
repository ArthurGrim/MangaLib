package de.mangalib.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.mangalib.entity.Band;

public interface BandRepository extends JpaRepository<Band, Long> {
    @Query("select b.istSpecial, count(b) from Band b group by b.istSpecial")
    List<Object[]> statisticsBySpecialState();

    @Query("select b.istGelesen, count(b) from Band b group by b.istGelesen")
    List<Object[]> statisticsByReadState();

    interface ReadingEntry {
        Integer getMultiplier();
        Integer getReread();
    }

    @Query("select s.multiplikator as multiplier, b.reread as reread from Band b "
            + "left join b.mangaReihe r left join r.mangaDetails d left join d.sammelbaende s "
            + "where b.istGelesen = true")
    List<ReadingEntry> readingEntries();

    boolean existsByBandIndex(Integer bandIndex);

    @Query("SELECT b FROM Band b WHERE b.mangaReihe.id = :mangaReiheId AND b.bandNr = "
            + "(SELECT MIN(v.bandNr) FROM Band v WHERE v.mangaReihe.id = :mangaReiheId AND v.bandNr > 0)")
    Optional<Band> findFirstBandByMangaReiheId(@Param("mangaReiheId") Long mangaReiheId);

    List<Band> findByMangaReiheId(Long mangaReiheId);

    @Query("SELECT b FROM Band b WHERE YEAR(b.erstelltAm) = ?1")
    List<Band> findByErstelltAmYear(int jahr);

    @Query("SELECT b FROM Band b WHERE YEAR(b.erstelltAm) = ?1 AND MONTH(b.erstelltAm) = ?2")
    List<Band> findByErstelltAmYearAndMonth(int jahr, int monat);

}
