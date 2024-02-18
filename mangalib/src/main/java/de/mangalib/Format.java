package de.mangalib;

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
    private Long formatID;

    @Column(name = "bezeichnung")
    private String bezeichnung;

}
