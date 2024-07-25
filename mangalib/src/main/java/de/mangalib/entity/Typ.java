package de.mangalib.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "typen")
@Getter @Setter @NoArgsConstructor
public class Typ {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "typ_id")
    private Long typId;

    @Column(name = "bezeichnung")
    private String bezeichnung;

    @OneToMany(mappedBy = "typ", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<MangaReihe> mangaReihen;
}
