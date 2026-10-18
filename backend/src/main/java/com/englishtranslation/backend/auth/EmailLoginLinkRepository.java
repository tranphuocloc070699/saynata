package com.englishtranslation.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailLoginLinkRepository extends JpaRepository<EmailLoginLink, String> {

    /** Deletes the link. Returns 1 for the one request that used it, so a link works only once, even in a race. */
    @Transactional
    @Modifying
    @Query("delete from EmailLoginLink l where l.tokenHash = :hash")
    int deleteLink(@Param("hash") String tokenHash);

    /** A new link replaces the old ones, and deleting an account removes its links. */
    @Transactional
    @Modifying
    @Query("delete from EmailLoginLink l where l.email = :email")
    int deleteAllFor(@Param("email") String email);
}
