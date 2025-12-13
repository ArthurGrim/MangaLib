package de.mangalib.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

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
    @JsonManagedReference
    private List<MangaReihe> mangaReihen;

}
