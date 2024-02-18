package de.mangalib;

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
    private Long typID;

    @Column(name = "bezeichnung")
    private String bezeichnung;

}

