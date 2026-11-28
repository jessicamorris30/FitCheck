package com.fitcheck.calendar.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of POST /api/calendar: put one of your outfits on a day.
 *
 * @param date     the day, as "2026-12-04" (JSON has no date type; Jackson reads this format)
 * @param outfitId one of your saved outfits
 */
public record PlanOutfitRequest(
        @NotNull(message = "Date is required")
        LocalDate date,

        @NotNull(message = "Outfit is required")
        UUID outfitId) {
}
