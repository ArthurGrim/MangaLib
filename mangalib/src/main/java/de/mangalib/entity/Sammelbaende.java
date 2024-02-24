package de.mangalib.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sammelbaende")
@Getter
@Setter
@NoArgsConstructor
public class Sammelbaende {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int multiplikator;

    @Column(nullable = false)
    private String typ;

}
