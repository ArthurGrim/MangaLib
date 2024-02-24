package de.mangalib.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mangareihe")
@Getter
@Setter
@NoArgsConstructor
public class MangaReihe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manga_index")
    private Integer mangaIndex;

    @ManyToOne
    @JoinColumn(name = "status_id", referencedColumnName = "status_id")
    private Status status;

    @ManyToOne
    @JoinColumn(name = "verlag_id", referencedColumnName = "verlag_id")
    private Verlag verlag;

    @ManyToOne
    @JoinColumn(name = "typ_id", referencedColumnName = "typ_id")
    private Typ typ;

    @ManyToOne
    @JoinColumn(name = "format_id", referencedColumnName = "format_id")
    private Format format;

    @Column(name = "titel")
    private String titel;

    @Column(name = "anzahl_baende")
    private Integer anzahlBaende;

    @Column(name = "preis_pro_band")
    private Double preisProBand;

    @Transient
    private String preisProBandString;

    @Column(name = "gesamtpreis")
    private Double gesamtpreis;

    @Transient
    private String gesamtpreisString;

    @Column(name = "erstellt_am")
    private java.sql.Timestamp erstelltAm;

    @Column(name = "aktualisiert_am")
    private java.sql.Timestamp aktualisiertAm;

    @Column(name = "ist_ebay_preis")
    private Boolean istEbayPreis;

    @Column(name = "ist_vergriffen")
    private Boolean istVergriffen;

    @OneToOne(mappedBy = "mangaReihe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private MangaDetails mangaDetails;

}
