package de.mangalib.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sammelbaende")
@Getter
@Setter
@NoArgsConstructor
public class Sammelband {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int multiplikator;

    @Column(nullable = false)
    private String typ;

    @OneToMany(mappedBy = "sammelbaende", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<MangaDetails> mangaDetails;

}
