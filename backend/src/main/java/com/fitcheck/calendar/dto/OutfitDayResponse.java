package com.fitcheck.calendar.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One day an outfit is on, for the outfit's own page ("Planned: Fri 4 Dec", "Last worn: ...").
 *
 * @param id   the calendar entry's id (to remove it), not the outfit's
 * @param date the day, sent as "2026-12-04"
 */
public record OutfitDayResponse(UUID id, LocalDate date) {
}
