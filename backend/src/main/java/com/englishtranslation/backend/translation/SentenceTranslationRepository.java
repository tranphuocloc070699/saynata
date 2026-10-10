package com.englishtranslation.backend.translation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentenceTranslationRepository extends JpaRepository<SentenceTranslation, Long> {

    Optional<SentenceTranslation> findByCacheKey(String cacheKey);
}
