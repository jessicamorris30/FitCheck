package com.fitcheck.auth;

/**
 * Data access for RefreshToken (find by token hash, revoke by family).
 * TODO: extend JpaRepository<RefreshToken, UUID> once RefreshToken is an entity.
 */
public interface RefreshTokenRepository {

    // TODO: query methods.
}
