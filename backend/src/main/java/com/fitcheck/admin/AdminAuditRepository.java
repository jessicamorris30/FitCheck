package com.fitcheck.admin;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {

    /** Newest first. */
    Page<AdminAuditEntry> findAllByOrderByCreatedAtDesc(Pageable page);

    Page<AdminAuditEntry> findAllByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId, Pageable page);
}
