package de.mangalib.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "formate")
@Getter @Setter @NoArgsConstructor
public class Format {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "format_id")
    private Long formatId;

    @Column(name = "bezeichnung")
    private String bezeichnung;

    @OneToMany(mappedBy = "format", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<MangaReihe> mangaReihen;

}

