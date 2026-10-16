package com.englishtranslation.backend.dictionary;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DictionaryWordRepository extends JpaRepository<DictionaryWord, Long> {

    /** Loads the senses in the same query, so a page of 200 words is not 201 queries. */
    @EntityGraph(attributePaths = "senses")
    List<DictionaryWord> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "senses")
    Optional<DictionaryWord> findWithSensesById(Long id);

    /** The bundled word with this lemma (lower case), with its senses. Empty when the word is not bundled. */
    @EntityGraph(attributePaths = "senses")
    Optional<DictionaryWord> findWithSensesByLemma(String lemma);

    /** Which of these lemmas (lower case) are bundled. One query, for checking a whole sentence. */
    @Query("select d.lemma from DictionaryWord d where d.lemma in :lemmas")
    List<String> findLemmasIn(@Param("lemmas") Collection<String> lemmas);
}
