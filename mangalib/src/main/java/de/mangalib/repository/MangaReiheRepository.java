package de.mangalib.repository;

import de.mangalib.MangaReihe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MangaReiheRepository extends JpaRepository<MangaReihe, Long> {
    List<MangaReihe> findByStatusId(Long statusId);
    List<MangaReihe> findByVerlagId(Long verlagId);
    List<MangaReihe> findByTypId(Long typId);
    List<MangaReihe> findByFormatId(Long formatId);
    List<MangaReihe> findByMangaIndex(Integer mangaIndex);
    List<MangaReihe> findByTitelContainingIgnoreCase(String titel);

    @Query("SELECT m FROM MangaReihe m WHERE YEAR(m.erstelltAm) = :jahr")
    List<MangaReihe> findByErstelltAmYear(int jahr);

    @Query("SELECT m FROM MangaReihe m WHERE YEAR(m.erstelltAm) = :jahr AND MONTH(m.erstelltAm) = :monat")
    List<MangaReihe> findByErstelltAmYearAndMonth(int jahr, int monat);
}
