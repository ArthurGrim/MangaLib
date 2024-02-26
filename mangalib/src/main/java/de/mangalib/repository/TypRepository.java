package de.mangalib.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.mangalib.entity.Typ;

@Repository
public interface TypRepository extends JpaRepository<Typ, Long> {

    Optional<Typ> findByBezeichnung(String name);
}

