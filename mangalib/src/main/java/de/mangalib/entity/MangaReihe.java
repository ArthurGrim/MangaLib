package de.mangalib.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

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
    @JsonBackReference
    @JoinColumn(name = "status_id", referencedColumnName = "status_id")
    private Status status;

    @ManyToOne
    @JsonBackReference
    @JoinColumn(name = "verlag_id", referencedColumnName = "verlag_id")
    private Verlag verlag;

    @ManyToOne
    @JsonBackReference
    @JoinColumn(name = "typ_id", referencedColumnName = "typ_id")
    private Typ typ;

    @ManyToOne
    @JsonBackReference
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

    @Column(name = "gesamtpreis", precision = 10, scale = 2)
    private BigDecimal gesamtpreis;

    @Transient
    private String gesamtpreisString;

    @Column(name = "aenderung_gesamtpreis", precision = 10, scale = 2)
    private BigDecimal aenderungGesamtpreis;

    @CreationTimestamp
    @Temporal(TemporalType.DATE) // Nur das Datum ohne Uhrzeit
    @Column(name = "erstellt_am")
    private LocalDate erstelltAm;

    @UpdateTimestamp
    @Temporal(TemporalType.DATE) // Nur das Datum ohne Uhrzeit
    @Column(name = "aktualisiert_am")
    private LocalDate aktualisiertAm;

    @Column(name = "ist_ebay_preis")
    private Boolean istEbayPreis;

    @Column(name = "ist_vergriffen")
    private Boolean istVergriffen;

    @JsonManagedReference
    @OneToOne(mappedBy = "mangaReihe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private MangaDetails mangaDetails;


}
