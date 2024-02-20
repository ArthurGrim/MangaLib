package de.mangalib;

import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verlage")
@Getter @Setter @NoArgsConstructor
public class Verlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verlag_id")
    private Long verlagId;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "verlag", cascade = CascadeType.ALL)
    private List<MangaReihe> mangaReihen;

}
