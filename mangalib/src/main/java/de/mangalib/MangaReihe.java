package de.mangalib;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mangareihe")
@Getter @Setter @NoArgsConstructor
public class MangaReihe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mangaIndex")
    private Integer mangaIndex;

    @Column(name = "statusID")
    private Status statusID;

    @Column(name = "verlagID")
    private Verlage verlagID;

    @Column(name = "typID")
    private Typ typID;

    @Column(name = "formatID")
    private Format formatID;

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


