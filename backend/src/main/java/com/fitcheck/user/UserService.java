package com.fitcheck.user;

import com.fitcheck.common.exception.FieldErrorException;
import com.fitcheck.common.exception.NotFoundException;
import com.fitcheck.user.dto.ProfileRequest;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final String USERNAME_TAKEN = "That username is already taken";
    private static final Pattern USERNAME = Pattern.compile(ProfileRules.USERNAME_REGEX);

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

    /**
     * Why this username can't be used, or null if it can. Shared by sign-up, profile editing and
     * the live "is it available?" check, so all three always agree.
     *
     * @param username    already normalized (trimmed, lower case)
     * @param currentUser the user asking (keeping their own username is fine); null at sign-up
     */
    @Transactional(readOnly = true)
    public String usernameProblem(String username, UUID currentUser) {
        if (username == null || username.isEmpty()) {
            return "Username is required";
        }
        if (!USERNAME.matcher(username).matches()) {
            return ProfileRules.USERNAME_MESSAGE;
        }
        if (ProfileRules.isReserved(username)) {
            return "That username isn't available";
        }
        boolean takenBySomeoneElse = users.findByUsernameIgnoreCase(username)
                .map(owner -> !owner.getId().equals(currentUser))
                .orElse(false);
        return takenBySomeoneElse ? USERNAME_TAKEN : null;
    }

    /**
     * Sets or changes the user's username and name ("finish your profile" and the Account page).
     *
     * @throws FieldErrorException (400, fieldErrors.username) if the username is reserved or taken
     */
    @Transactional
    public User updateProfile(UUID userId, ProfileRequest request) {
        User user = getById(userId);
        String problem = usernameProblem(request.username(), userId);
        if (problem != null) {
            throw new FieldErrorException("username", problem);
        }
        user.setProfile(request.username(), request.firstName(), request.lastName());
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            // Someone else took it in the same instant; the unique index (V7) stopped the second one.
            throw new FieldErrorException("username", USERNAME_TAKEN);
        }
        return user;
    }
}
