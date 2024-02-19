package de.mangalib.repository;

import de.mangalib.Format;
import de.mangalib.MangaReihe;
import de.mangalib.Status;
import de.mangalib.Typ;
import de.mangalib.Verlag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MangaReiheRepository extends JpaRepository<MangaReihe, Long> {
     List<MangaReihe> findByStatus(Status status);
    List<MangaReihe> findByVerlag(Verlag verlag);
    List<MangaReihe> findByTyp(Typ typ);
    List<MangaReihe> findByFormat(Format format);
    List<MangaReihe> findByMangaIndex(Integer mangaIndex);
    List<MangaReihe> findByTitelContainingIgnoreCase(String titel);

    @Query("SELECT m FROM MangaReihe m WHERE YEAR(m.erstelltAm) = :jahr")
    List<MangaReihe> findByErstelltAmYear(int jahr);

    @Query("SELECT m FROM MangaReihe m WHERE YEAR(m.erstelltAm) = :jahr AND MONTH(m.erstelltAm) = :monat")
    List<MangaReihe> findByErstelltAmYearAndMonth(int jahr, int monat);
}
