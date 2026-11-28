package com.fitcheck.calendar;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** The outfit calendar (/api/calendar) end to end. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ClothingItemRepository clothing;

    @Autowired
    private JwtService jwt;

    @PersistenceContext
    private EntityManager em;

    private User alice;
    private User bob;
    private ClothingItem tee;

    @BeforeEach
    void setUp() {
        alice = users.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        bob = users.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
        tee = clothing.saveAndFlush(new ClothingItem(alice, "White tee", ClothingCategory.TOP, null, List.of(),
                "users/" + alice.getId() + "/" + UUID.randomUUID() + ".jpg"));
    }

    @Test
    void severalOutfitsOnOneDayInTheOrderAdded() throws Exception {
        String work = outfit(alice, "Work");
        String gym = outfit(alice, "Gym");

        plan(alice, "2026-12-04", gym)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.date").value("2026-12-04"))
                .andExpect(jsonPath("$.outfit.name").value("Gym"));
        plan(alice, "2026-12-04", work).andExpect(status().isCreated());

        list(alice, "2026-12-04", "2026-12-04")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].outfit.name", contains("Gym", "Work")))
                // The whole outfit comes along, photos included, so the calendar needs no more requests.
                .andExpect(jsonPath("$[0].outfit.items[0].name").value("White tee"))
                .andExpect(jsonPath("$[0].outfit.items[0].imageUrl", containsString("sig=")));
    }

    @Test
    void listsOnlyTheAskedRangeByDay() throws Exception {
        String work = outfit(alice, "Work");
        String gym = outfit(alice, "Gym");
        plan(alice, "2026-12-10", work);          // after the range
        plan(alice, "2026-12-06", gym);           // Sunday: last day, included
        plan(alice, "2026-11-30", work);          // Monday: first day, included
        plan(alice, "2026-11-29", gym);           // before the range

        list(alice, "2026-11-30", "2026-12-06")
                .andExpect(jsonPath("$[*].date", contains("2026-11-30", "2026-12-06")));
    }

    @Test
    void theSameOutfitTwiceOnOneDayIsRefusedButOtherDaysAreFine() throws Exception {
        String work = outfit(alice, "Work");
        plan(alice, "2026-12-04", work).andExpect(status().isCreated());

        plan(alice, "2026-12-04", work)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That outfit is already on this day"));
        plan(alice, "2026-12-05", work).andExpect(status().isCreated());
    }

    @Test
    void cantPlanSomeoneElsesOutfitOrSeeOrRemoveTheirDays() throws Exception {
        String alicesOutfit = outfit(alice, "Work");
        String entry = id(plan(alice, "2026-12-04", alicesOutfit));

        // Same answer as an outfit that doesn't exist: nothing to learn about other people's ids.
        plan(bob, "2026-12-04", alicesOutfit).andExpect(status().isNotFound());
        plan(bob, "2026-12-04", UUID.randomUUID().toString()).andExpect(status().isNotFound());
        list(bob, "2026-12-01", "2026-12-31").andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/calendar/" + entry).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        list(alice, "2026-12-04", "2026-12-04").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void removingTakesItOffTheDayButKeepsTheOutfit() throws Exception {
        String work = outfit(alice, "Work");
        String entry = id(plan(alice, "2026-12-04", work));

        mvc.perform(delete("/api/calendar/" + entry).header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());

        list(alice, "2026-12-04", "2026-12-04").andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/outfits/" + work).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAnOutfitRemovesItFromTheCalendar() throws Exception {
        String work = outfit(alice, "Work");
        plan(alice, "2026-12-04", work);
        plan(alice, "2026-12-11", work);
        // In this test every request shares one database session (so it can all be rolled back);
        // in the real app each request starts fresh. Forget what's loaded, like a new request would.
        em.flush();
        em.clear();

        mvc.perform(delete("/api/outfits/" + work).header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());

        list(alice, "2026-12-01", "2026-12-31").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aDayHoldsAtMostTenOutfits() throws Exception {
        for (int i = 0; i < CalendarService.MAX_PER_DAY; i++) {
            plan(alice, "2026-12-04", outfit(alice, "Outfit " + i)).andExpect(status().isCreated());
        }
        plan(alice, "2026-12-04", outfit(alice, "One too many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A day can have at most 10 outfits"));
    }

    @Test
    void badDatesAndRangesAreRefused() throws Exception {
        String work = outfit(alice, "Work");

        list(alice, "2026-12-06", "2026-11-30").andExpect(status().isBadRequest()); // backwards
        list(alice, "2026-01-01", "2026-03-04").andExpect(status().isBadRequest()); // 63 days
        list(alice, "2026-01-01", "2026-03-03").andExpect(status().isOk());         // 62 days: fine
        list(alice, "not-a-date", "2026-12-06").andExpect(status().isBadRequest());
        mvc.perform(get("/api/calendar").header("Authorization", bearer(alice)))   // no range at all
                .andExpect(status().isBadRequest());

        plan(alice, "1999-12-31", work).andExpect(status().isBadRequest());
        plan(alice, "2026-02-30", work).andExpect(status().isBadRequest());         // no such day
        mvc.perform(post("/api/calendar").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outfitId\":\"" + work + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").value("Date is required"));
    }

    @Test
    void needsLogin() throws Exception {
        mvc.perform(get("/api/calendar").param("from", "2026-12-01").param("to", "2026-12-07"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    /** Creates an outfit (with the white tee) through the API and returns its id. */
    private String outfit(User owner, String name) throws Exception {
        return id(mvc.perform(post("/api/outfits").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"itemIds\":[\"" + tee.getId() + "\"]}")));
    }

    private ResultActions plan(User user, String date, String outfitId) throws Exception {
        return mvc.perform(post("/api/calendar").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"outfitId\":\"" + outfitId + "\"}"));
    }

    private ResultActions list(User user, String from, String to) throws Exception {
        return mvc.perform(get("/api/calendar").header("Authorization", bearer(user))
                .param("from", from).param("to", to));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
    }
}
