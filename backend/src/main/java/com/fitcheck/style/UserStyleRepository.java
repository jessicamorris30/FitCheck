package com.fitcheck.style;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserStyleRepository extends JpaRepository<UserStyle, UUID> {

    /**
     * The row, locked until the transaction ends ("SELECT ... FOR UPDATE"), so two changes at the
     * same moment (two photos uploaded together) can't both read version 3 and both write 4.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserStyle> findForUpdateByUserId(UUID userId);
}
