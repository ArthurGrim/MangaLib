package de.mangalib.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "einkaufsliste")
@Getter
@Setter
public class EinkaufslisteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manga_index")
    private Integer mangaIndex;

    @ManyToOne
    @JoinColumn(name = "verlag_id", referencedColumnName = "verlag_id")
    private Verlag verlagId;

    @ManyToOne
    @JoinColumn(name = "typ_id", referencedColumnName = "typ_id")
    private Typ typId;

    @ManyToOne
    @JoinColumn(name = "format_id", referencedColumnName = "format_id")
    private Format formatId;

    @Column(name = "titel")
    private String titel;

    @Column(name = "anzahl_baende")
    private Integer anzahlBaende;

    @Column(name = "preis", precision = 10, scale = 2)
    private BigDecimal preis;

    @Column(name = "gesamtpreis", precision = 10, scale = 2)
    private BigDecimal gesamtpreis;

    @Column(name = "aenderung_gesamtpreis", precision = 10, scale = 2)
    private BigDecimal aenderungGesamtpreis;

    @Column(name = "erscheinungsdatum")
    private LocalDate erscheinungsdatum;

    @Column(name = "gekauft")
    private Boolean gekauft;

    @Column(name = "status_de")
    private String statusDe;

    @Column(name = "anzahl_baende_de")
    private Integer anzahlBaendeDe;

    @Column(name = "status_erstv")
    private String statusErstv;

    @Column(name = "herkunft")
    private String herkunft;

    @Column(name = "start_jahr")
    private Integer startJahr;

    @Column(name = "anzahl_baende_erstv")
    private Integer anzahlBaendeErstv;

    @ManyToOne
    @JoinColumn(name = "sammelbaende_id", referencedColumnName = "id")
    private Sammelband sammelbaendeId;

    @Column(name = "anilist_url")
    private String anilistUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "ist_ebay_preis")
    private Boolean istEbayPreis;

    @Column(name = "ist_vergriffen")
    private Boolean istVergriffen;
}
