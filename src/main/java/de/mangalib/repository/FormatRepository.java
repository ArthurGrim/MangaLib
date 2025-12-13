package de.mangalib.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.mangalib.entity.Format;

@Repository
public interface FormatRepository extends JpaRepository<Format, Long> {

    Optional<Format> findByBezeichnung(String name);
}
