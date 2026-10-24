package com.englishtranslation.backend.lines;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineTranslationRepository extends JpaRepository<LineTranslation, Long> {

    List<LineTranslation> findByTargetLangAndPromptVersionAndTextHashIn(String targetLang, String promptVersion,
            Collection<String> textHashes);
}
