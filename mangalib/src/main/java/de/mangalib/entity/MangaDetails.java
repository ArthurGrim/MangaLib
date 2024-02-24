package de.mangalib.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mangadetails")
@Getter
@Setter
@NoArgsConstructor
public class MangaDetails {

    @Id
    @Column(name = "id")
    private Long id;

    // Andere Attribute...

    @Column(name = "sammelbaende_id")
    private Long sammelbaendeId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id", referencedColumnName = "id")
    private MangaReihe mangaReihe;

    @ManyToOne
    @JoinColumn(name = "sammelbaende_id", insertable = false, updatable = false)
    private Sammelbaende sammelbaende;

    // Getter und Setter
}
