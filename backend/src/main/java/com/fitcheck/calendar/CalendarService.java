package com.fitcheck.calendar;

import com.fitcheck.calendar.dto.OutfitDayResponse;
import com.fitcheck.calendar.dto.PlanOutfitRequest;
import com.fitcheck.calendar.dto.PlannedOutfitResponse;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.ConflictException;
import com.fitcheck.common.exception.NotFoundException;
import com.fitcheck.outfit.Outfit;
import com.fitcheck.outfit.OutfitService;
import com.fitcheck.user.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outfit calendar: putting saved outfits on days, listing a week or month, removing them.
 * Every method takes the owner's id (from the access token) and only touches that owner's data.
 */
@Service
public class CalendarService {

    /** Longest range one request may ask for: a month view shows up to 6 weeks (42 days). */
    static final int MAX_RANGE_DAYS = 62;
    /** Outfits on one day. Generous (work, gym, dinner, ...), but keeps the day view sane. */
    static final int MAX_PER_DAY = 10;
    /** Sensible dates only, so typos like year 20026 don't end up in the calendar. */
    static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    private final PlannedOutfitRepository plans;
    private final OutfitService outfits;
    private final UserRepository users;

    public CalendarService(PlannedOutfitRepository plans, OutfitService outfits, UserRepository users) {
        this.plans = plans;
        this.outfits = outfits;
        this.users = users;
    }

    /**
     * The owner's planned outfits from {@code from} to {@code to} (both included), by day and
     * then in the order they were added.
     *
     * @throws BadRequestException if the range is backwards, too long, or outside 2000-2100
     */
    @Transactional(readOnly = true)
    public List<PlannedOutfitResponse> list(UUID ownerId, LocalDate from, LocalDate to) {
        checkInRange(from);
        checkInRange(to);
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must be on or after 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BadRequestException("Ask for at most " + MAX_RANGE_DAYS + " days at a time");
        }
        return plans.findAllByOwner_IdAndDayBetweenOrderByDayAscCreatedAtAscIdAsc(ownerId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Every day one of the owner's outfits is on, earliest first (past and future).
     *
     * @throws NotFoundException if the outfit isn't one of the owner's
     */
    @Transactional(readOnly = true)
    public List<OutfitDayResponse> daysFor(UUID ownerId, UUID outfitId) {
        Outfit outfit = outfits.findOwned(ownerId, outfitId);
        return plans.findAllByOutfit_IdOrderByDayAsc(outfit.getId()).stream()
                .map(p -> new OutfitDayResponse(p.getId(), p.getDay()))
                .toList();
    }

    /**
     * Puts one of the owner's outfits on a day. A day can hold several different outfits.
     *
     * @throws NotFoundException   if the outfit isn't one of the owner's (same answer as "doesn't
     *                             exist", so other people's outfit ids can't be probed)
     * @throws ConflictException   if that outfit is already on that day
     * @throws BadRequestException if the date is outside 2000-2100 or the day is full
     */
    @Transactional
    public PlannedOutfitResponse plan(UUID ownerId, PlanOutfitRequest request) {
        LocalDate day = request.date();
        checkInRange(day);
        Outfit outfit = outfits.findOwned(ownerId, request.outfitId());
        if (plans.existsByOutfit_IdAndDay(outfit.getId(), day)) {
            throw new ConflictException("That outfit is already on this day");
        }
        if (plans.countByOwner_IdAndDay(ownerId, day) >= MAX_PER_DAY) {
            throw new BadRequestException("A day can have at most " + MAX_PER_DAY + " outfits");
        }
        try {
            PlannedOutfit saved = plans.saveAndFlush(new PlannedOutfit(users.getReferenceById(ownerId), outfit, day));
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // Two clicks at the same moment: the check above passed for both, the database's
            // unique rule (V6) stopped the second one.
            throw new ConflictException("That outfit is already on this day");
        }
    }

    /**
     * Takes an outfit off a day. The outfit itself is untouched.
     *
     * @throws NotFoundException if there's no such calendar entry owned by this user
     */
    @Transactional
    public void remove(UUID ownerId, UUID plannedOutfitId) {
        PlannedOutfit plan = plans.findByIdAndOwner_Id(plannedOutfitId, ownerId)
                .orElseThrow(() -> new NotFoundException("Calendar entry not found"));
        plans.delete(plan);
    }

    private static void checkInRange(LocalDate day) {
        if (day.isBefore(EARLIEST) || day.isAfter(LATEST)) {
            throw new BadRequestException("Dates must be between " + EARLIEST + " and " + LATEST);
        }
    }

    private PlannedOutfitResponse toResponse(PlannedOutfit plan) {
        return new PlannedOutfitResponse(plan.getId(), plan.getDay(), outfits.toResponse(plan.getOutfit()));
    }
}
