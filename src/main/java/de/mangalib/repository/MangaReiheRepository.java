package de.mangalib.repository;

import de.mangalib.entity.Format;
import de.mangalib.entity.MangaReihe;
import de.mangalib.entity.Status;
import de.mangalib.entity.Typ;
import de.mangalib.entity.Verlag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Query("SELECT MAX(m.id) FROM MangaReihe m")
    Long findMaxId();

    List<MangaReihe> findByTitel(String titel);

    @Query("SELECT SUM(m.anzahlBaende) FROM MangaReihe m WHERE m.status.id <> 4")
    Integer findeGesamtAnzahlBaende();

    @Query("SELECT SUM(mr.anzahlBaende * COALESCE(md.sammelbaende.multiplikator, 1)) " +
            "FROM MangaReihe mr " +
            "LEFT JOIN mr.mangaDetails md " +
            "LEFT JOIN md.sammelbaende sb " +
            "WHERE mr.status.id <> 4")
    Integer findeGesamtAnzahlMitMultiplikator();

    @Query("SELECT SUM(m.gesamtpreis) FROM MangaReihe m WHERE m.status.id <> 4")
    BigDecimal findeGesamtSummeGesamtpreis();

    @Query("SELECT COUNT(m) FROM MangaReihe m WHERE m.anzahlBaende = 1")
    int countReihenMitEinemBand();

    @Query("SELECT COUNT(m) FROM MangaReihe m WHERE m.anzahlBaende BETWEEN 2 AND 5")
    int countReihenMitZweiBisFuenfBaenden();

    @Query("SELECT COUNT(m) FROM MangaReihe m WHERE m.anzahlBaende BETWEEN 6 AND 10")
    int countReihenMitSechsBisZehnBaenden();

    @Query("SELECT COUNT(m) FROM MangaReihe m WHERE m.anzahlBaende BETWEEN 11 AND 20")
    int countReihenMitElfBisZwanzigBaenden();

    @Query("SELECT COUNT(m) FROM MangaReihe m WHERE m.anzahlBaende BETWEEN 21 AND 50")
    int countReihenMitEinundzwanzigBisFuenfzigBaenden();

    @Query("SELECT COUNT(m) FROM MangaReihe m WHERE m.anzahlBaende BETWEEN 51 AND 100")
    int countReihenMitEinundfuenfzigBisHundertBaenden();

    @Query("SELECT COUNT(m) FROM MangaReihe m WHERE m.anzahlBaende > 100")
    int countReihenMitMehrAlsHundertBaenden();

    @Query("SELECT mr FROM MangaReihe mr JOIN mr.mangaDetails md WHERE md.istGelesen = :istGelesen")
    List<MangaReihe> findByIstGelesen(@Param("istGelesen") Boolean istGelesen);
}
