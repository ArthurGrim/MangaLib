package de.mangalib.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "einkaufslistedetails")
@Getter
@Setter
public class EinkaufslisteItemDetails {

    @Id
    @Column (name = "id")
    private Long id;
    
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

    @Column(name = "anilist_url")
    private String anilistUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    @ManyToOne
    @JoinColumn(name = "sammelbaende_id", referencedColumnName = "id")
    private Sammelband sammelbaendeId;

    @JsonBackReference
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id", referencedColumnName = "id")
    private EinkaufslisteItem einkaufslisteItem;
}
