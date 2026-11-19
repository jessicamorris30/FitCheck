package com.fitcheck.user;

import com.fitcheck.common.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User lookups. Later: change password, delete account.
 */
@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * @throws NotFoundException if the user no longer exists (e.g. account deleted while their
     *         access token is still valid)
     */
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
