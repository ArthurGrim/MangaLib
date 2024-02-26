package de.mangalib.entity;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "baende")
@Getter
@Setter
public class Band {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "manga_reihe_id", nullable = false)
    private MangaReihe mangaReihe;

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

    public Band() {
        try {
            this.bildUrl = new URI("https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/No-Image-Placeholder.svg/330px-No-Image-Placeholder.svg.png?20200912122019").toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            // Loggen Sie den Fehler oder behandeln Sie ihn entsprechend
            e.printStackTrace();
        } 
    }
}
