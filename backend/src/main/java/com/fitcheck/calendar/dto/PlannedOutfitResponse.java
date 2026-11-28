package com.fitcheck.calendar.dto;

import com.fitcheck.outfit.dto.OutfitResponse;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One calendar entry: which outfit, on which day. Includes the whole outfit (name, clothes with
 * photo links) so the calendar can show it without asking for each outfit separately.
 *
 * @param id     the calendar entry's own id (used to remove it again), not the outfit's
 * @param date   the day, sent as "2026-12-04"
 * @param outfit the planned outfit
 */
public record PlannedOutfitResponse(UUID id, LocalDate date, OutfitResponse outfit) {
}
