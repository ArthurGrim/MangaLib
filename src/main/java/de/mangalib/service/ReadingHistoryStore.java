package de.mangalib.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Native Abfragen nutzen dieselbe JPA-Verbindung/Transaktion wie die Bibliothek.
 * Keine Entityregistrierung und keine DDL-Ausführung beim Start alter Schemas.
 */
@Repository
@ConditionalOnProperty(name = "mangalib.reading-history.enabled", havingValue = "true")
public class ReadingHistoryStore {
    @PersistenceContext private EntityManager em;

    public record Event(long id, long bandId, UUID operationId, LocalDate date,
                        LocalDate originalDate, ReadingHistoryStatistics.Kind kind, int revision,
                        Integer volumeFactor) {}

    private static final String COLUMNS = "id, band_id, operation_id, completion_date, initial_completion_date, kind, revision, volume_factor";

    /** Explizite Aktivierung darf bei fehlendem oder veraltetem Schema nicht still zurückfallen. */
    public void validateSchema() {
        em.createNativeQuery("select " + COLUMNS + ", created_at from band_reading_events where 1=0").getResultList();
        var engine = em.createNativeQuery("select ENGINE from information_schema.TABLES where TABLE_SCHEMA = database() and TABLE_NAME = 'band_reading_events'")
                .getSingleResult();
        var unique = em.createNativeQuery("""
                select count(*) from (select INDEX_NAME from information_schema.STATISTICS
                where TABLE_SCHEMA = database() and TABLE_NAME = 'band_reading_events' and NON_UNIQUE = 0
                group by INDEX_NAME having group_concat(COLUMN_NAME order by SEQ_IN_INDEX) = 'operation_id,band_id') expected_index
                """).getSingleResult();
        var foreignKey = em.createNativeQuery("""
                select count(*) from information_schema.KEY_COLUMN_USAGE k
                join information_schema.REFERENTIAL_CONSTRAINTS r
                on r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA and r.TABLE_NAME=k.TABLE_NAME and r.CONSTRAINT_NAME=k.CONSTRAINT_NAME
                where k.TABLE_SCHEMA=database() and k.TABLE_NAME='band_reading_events' and k.COLUMN_NAME='band_id'
                and k.REFERENCED_TABLE_SCHEMA=database() and k.REFERENCED_TABLE_NAME='baende' and k.REFERENCED_COLUMN_NAME='id'
                and r.DELETE_RULE='RESTRICT' and r.UPDATE_RULE='RESTRICT'
                """).getSingleResult();
        if (!"InnoDB".equals(engine) || ((Number) unique).intValue() != 1 || ((Number) foreignKey).intValue() != 1)
            throw new IllegalStateException("Lesehistorie benötigt das geprüfte S03-Schema.");
    }

    public List<Event> forBand(long bandId) {
        return read("select " + COLUMNS + " from band_reading_events where band_id = :id order by completion_date, id", bandId);
    }

    public List<Event> forOperation(UUID operation) {
        return read("select " + COLUMNS + " from band_reading_events where operation_id = :id order by band_id", operation.toString());
    }

    public List<Event> all() {
        return map(em.createNativeQuery("select " + COLUMNS + " from band_reading_events order by completion_date, id").getResultList());
    }

    private List<Event> read(String sql, Object id) {
        return map(em.createNativeQuery(sql).setParameter("id", id).getResultList());
    }

    private List<Event> map(List<?> rows) {
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new Event(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                    UUID.fromString(row[2].toString()), date(row[3]), date(row[4]),
                    ReadingHistoryStatistics.Kind.valueOf(row[5].toString()), ((Number) row[6]).intValue(),
                    row[7] == null ? null : ((Number) row[7]).intValue());
        }).toList();
    }

    private LocalDate date(Object value) {
        return value instanceof LocalDate date ? date : ((java.sql.Date) value).toLocalDate();
    }

    public void insert(long bandId, UUID operation, LocalDate date, ReadingHistoryStatistics.Kind kind, Integer factor) {
        em.flush();
        em.createNativeQuery("""
                insert into band_reading_events
                (band_id, operation_id, completion_date, initial_completion_date, kind, revision, volume_factor, created_at)
                values (:band, :operation, :date, :date, :kind, 0, :factor, :created)
                """)
                .setParameter("band", bandId).setParameter("operation", operation.toString())
                .setParameter("date", java.sql.Date.valueOf(date)).setParameter("kind", kind.name())
                .setParameter("factor", factor).setParameter("created", java.time.LocalDateTime.now(java.time.Clock.systemUTC()))
                .executeUpdate();
    }

    public boolean correct(long eventId, long bandId, int revision, LocalDate date) {
        return em.createNativeQuery("""
                update band_reading_events set completion_date = :date, revision = revision + 1
                where id = :id and band_id = :band and revision = :revision
                """).setParameter("date", java.sql.Date.valueOf(date)).setParameter("id", eventId)
                .setParameter("band", bandId).setParameter("revision", revision).executeUpdate() == 1;
    }
}
