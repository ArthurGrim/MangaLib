package de.mangalib;

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
    private Long verlagID;

    @Column(name = "name")
    private String name;

}

