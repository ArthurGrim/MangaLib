package de.mangalib;

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

    @Column(name = "mangaIndex")
    private Integer mangaIndex;

    @ManyToOne
    @JoinColumn(name = "statusID", referencedColumnName = "statusID")
    private Status status;

    @ManyToOne
    @JoinColumn(name = "verlagID", referencedColumnName = "verlagID")
    private Verlag verlag;

    @ManyToOne
    @JoinColumn(name = "typID", referencedColumnName = "typID")
    private Typ typ;

    @ManyToOne
    @JoinColumn(name = "formatID", referencedColumnName = "formatID")
    private Format format;

    @Column(name = "titel")
    private String titel;

    @Column(name = "anzahlBaende")
    private Integer anzahlBaende;

    @Column(name = "preisProBand")
    private Double preisProBand;

    @Column(name = "gesamtpreis")
    private Double gesamtpreis;

    @Column(name = "erstelltAm")
    private java.sql.Timestamp erstelltAm;

    @Column(name = "aktualisiertAm")
    private java.sql.Timestamp aktualisiertAm;

    @Column(name = "istEbayPreis")
    private Boolean istEbayPreis;

    @Column(name = "istVergriffen")
    private Boolean istVergriffen;

}
