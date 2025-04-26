package de.mangalib.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDate;

import de.mangalib.entity.EinkaufslisteItem;

@Repository
public interface EinkaufslisteRepository extends JpaRepository<EinkaufslisteItem, Long> {
    List<EinkaufslisteItem> findByErscheinungsdatumBetween(LocalDate start, LocalDate end, Sort sort);

    @Query("SELECT FUNCTION('MONTH', e.erscheinungsdatum), SUM(e.anzahlBaende) " +
            "FROM EinkaufslisteItem e " +
            "WHERE FUNCTION('YEAR', e.erscheinungsdatum) = :year " +
            "GROUP BY FUNCTION('MONTH', e.erscheinungsdatum) " +
            "ORDER BY FUNCTION('MONTH', e.erscheinungsdatum)")
    List<Object[]> findMonthlyBandDataByYear(int year);

    @Query("SELECT FUNCTION('MONTH', e.erscheinungsdatum), SUM(e.gesamtpreis) " +
            "FROM EinkaufslisteItem e " +
            "WHERE FUNCTION('YEAR', e.erscheinungsdatum) = :year " +
            "GROUP BY FUNCTION('MONTH', e.erscheinungsdatum) " +
            "ORDER BY FUNCTION('MONTH', e.erscheinungsdatum)")
    List<Object[]> findMonthlySpendingDataByYear(int year);

    @Query("SELECT DISTINCT FUNCTION('YEAR', e.erscheinungsdatum) FROM EinkaufslisteItem e ORDER BY FUNCTION('YEAR', e.erscheinungsdatum)")
    List<Integer> findDistinctYears();

    @Query("SELECT MONTH(e.erscheinungsdatum) as monat, SUM(e.anzahlBaende) as baende FROM EinkaufslisteItem e WHERE YEAR(e.erscheinungsdatum) = :jahr AND e.gekauft = true GROUP BY MONTH(e.erscheinungsdatum)")
    List<Object[]> findBandeProMonat(@Param("jahr") int jahr);

    @Query("SELECT MONTH(e.erscheinungsdatum) as monat, SUM(e.gesamtpreis) as geld FROM EinkaufslisteItem e WHERE YEAR(e.erscheinungsdatum) = :jahr AND e.gekauft = true GROUP BY MONTH(e.erscheinungsdatum)")
    List<Object[]> findGeldProMonat(@Param("jahr") int jahr);
}
