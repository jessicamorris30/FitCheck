package com.fitcheck.style;

import java.util.UUID;

/**
 * "A user's style photos or note changed": published inside the change's transaction, handled
 * after it commits by {@link StyleDescriber}, which rewrites Claude's private notes.
 *
 * @param version the style's version after this change (a newer change makes this one obsolete)
 */
public record StyleChanged(UUID userId, int version) {
}
