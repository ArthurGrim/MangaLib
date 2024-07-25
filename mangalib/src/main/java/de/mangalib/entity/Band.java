package de.mangalib.entity;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonBackReference;

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
    @JsonBackReference
    @JoinColumn(name = "manga_reihe_id", nullable = false)
    private MangaReihe mangaReihe;

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
    
    @Column(name = "aenderung_preis", precision = 10, scale = 2)
    private BigDecimal aenderungPreis;

    @CreationTimestamp
    @Temporal(TemporalType.DATE) // Nur das Datum ohne Uhrzeit
    @Column(name = "erstellt_am")
    private LocalDate erstelltAm;

    @UpdateTimestamp
    @Temporal(TemporalType.DATE) // Nur das Datum ohne Uhrzeit
    @Column(name = "aktualisiert_am")
    private LocalDate aktualisiertAm;

    @Column(name = "ist_gelesen")
    private boolean istGelesen;

    public Band() {
        try {
            this.bildUrl = new URI("https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/No-Image-Placeholder.svg/330px-No-Image-Placeholder.svg.png?20200912122019").toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            // Loggen Sie den Fehler oder behandeln Sie ihn entsprechend
            e.printStackTrace();
        } 
    }
}
