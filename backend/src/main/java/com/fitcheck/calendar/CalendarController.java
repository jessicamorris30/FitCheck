package com.fitcheck.calendar;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.calendar.dto.PlanOutfitRequest;
import com.fitcheck.calendar.dto.PlannedOutfitResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outfit calendar endpoints under /api/calendar. All require login; the owner always comes from
 * the token. Dates are plain days written as "2026-12-04".
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    /**
     * GET /api/calendar?from=2026-11-30&to=2026-12-06 → your planned outfits in that range (both
     * days included), by day and then in the order added. 400 if the range is invalid or over 62 days.
     */
    @GetMapping
    public List<PlannedOutfitResponse> list(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return calendar.list(me.id(), from, to);
    }

    /**
     * POST /api/calendar {"date":"2026-12-04","outfitId":"..."} → 201 + the entry.
     * 404 if the outfit isn't yours, 409 if it's already on that day, 400 if the day is full.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedOutfitResponse plan(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody PlanOutfitRequest request) {
        return calendar.plan(me.id(), request);
    }

    /** DELETE /api/calendar/{id} → 204. Only takes it off the day; the outfit stays. 404 if not yours. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        calendar.remove(me.id(), id);
    }
}
