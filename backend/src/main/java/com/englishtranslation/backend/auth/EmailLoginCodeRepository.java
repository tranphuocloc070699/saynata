package com.englishtranslation.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailLoginCodeRepository extends JpaRepository<EmailLoginCode, String> {

    /**
     * Counts one guess, in one atomic statement. Returns 0 when there is no code or the guesses are used up,
     * so two requests at the same moment cannot both get the "last" guess.
     */
    @Transactional
    @Modifying
    @Query("update EmailLoginCode c set c.attempts = c.attempts + 1 where c.email = :email and c.attempts < :max")
    int countGuess(@Param("email") String email, @Param("max") int max);

    /** Deletes the code. Returns 1 for the one request that used it, so a code works only once. */
    @Transactional
    @Modifying
    @Query("delete from EmailLoginCode c where c.email = :email")
    int deleteCode(@Param("email") String email);
}
