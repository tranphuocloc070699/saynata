package com.saynata.backend.word;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring writes the implementation. Method names become SQL (derived queries). */
public interface WordRepository extends JpaRepository<Word, Long> {

    Optional<Word> findByHeadwordIgnoreCase(String headword);

    boolean existsByHeadwordIgnoreCase(String headword);
}
