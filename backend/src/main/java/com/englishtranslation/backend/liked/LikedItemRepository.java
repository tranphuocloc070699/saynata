package com.englishtranslation.backend.liked;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LikedItemRepository extends JpaRepository<LikedItem, Long> {

    List<LikedItem> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<LikedItem> findByUserIdAndKindAndTextKey(Long userId, String kind, String textKey);

    long countByUserId(Long userId);

    @Transactional
    @Modifying
    @Query("delete from LikedItem i where i.userId = :userId and i.kind = :kind and i.textKey = :textKey")
    int deleteItem(@Param("userId") Long userId, @Param("kind") String kind, @Param("textKey") String textKey);
}
