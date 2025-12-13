package de.mangalib.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "status")
@Getter @Setter @NoArgsConstructor
public class Status {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "beschreibung")
    private String beschreibung;

    @OneToMany(mappedBy = "status", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<MangaReihe> mangaReihen;

}
