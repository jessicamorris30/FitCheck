package com.fitcheck.admin;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    Optional<RecoveryCode> findByUser_IdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    long countByUser_IdAndUsedAtIsNull(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecoveryCode c where c.user.id = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
