package com.englishtranslation.backend.word;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordLookupRepository extends JpaRepository<WordLookup, Long> {

    Optional<WordLookup> findByCacheKey(String cacheKey);
}
