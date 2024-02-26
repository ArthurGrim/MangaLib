package de.mangalib.entity;

import java.math.BigDecimal;
import java.net.URL;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "baende")
@Getter
@Setter
@NoArgsConstructor
public class Band {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manga_reihe_id", nullable = false)
    private Long mangaReiheId;

    @Column(name = "band_index")
    private Integer bandIndex;

    @Column(name = "band_nr")
    private Integer bandNr;

    @Column(name = "preis")
    private BigDecimal preis;

    @Column(name = "bild_url")
    private URL bildUrl;

    @Column(name = "mp_url")
    private URL mpUrl;

    @Column(name = "ist_special")
    private Boolean istSpecial;   
}
