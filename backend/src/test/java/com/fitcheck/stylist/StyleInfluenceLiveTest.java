package com.fitcheck.stylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fitcheck.stylist.StylistModels.Suggestion;
import com.fitcheck.stylist.StylistModels.SuggestionRequest;
import com.fitcheck.stylist.StylistModels.WardrobeEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Calls the REAL Claude API (two text-only requests, a few cents) to check that the user's style
 * notes really steer the stylist (step 7.5), and that a request asking for something different
 * still wins for that request. Tagged "live": run on purpose with
 *
 * <pre>.\mvnw.cmd test -Dtest=StyleInfluenceLiveTest -Dtest.excludedGroups=none</pre>
 */
@Tag("live")
@SpringBootTest
class StyleInfluenceLiveTest {

    private static final String NOTES = "This user's style is minimalist and monochrome: black, white and grey, "
            + "clean tailored lines, no prints, no sparkle. They never wear heels; always suggest flat shoes "
            + "such as loafers. Outerwear is a structured camel coat.";

    private static final WardrobeEntry TROUSERS = item("Black wide-leg trousers", "BOTTOM", "black");
    private static final WardrobeEntry SHIRT = item("Crisp white shirt", "TOP", "white");
    private static final WardrobeEntry SEQUIN_DRESS = item("Red sequin mini dress", "DRESS", "red");
    private static final WardrobeEntry STILETTOS = item("Silver stiletto heels", "SHOES", "silver");
    private static final WardrobeEntry LOAFERS = item("Black leather loafers", "SHOES", "black");
    private static final WardrobeEntry HOODIE = item("Neon green hoodie", "TOP", "neon green");
    private static final WardrobeEntry JEANS = item("Ripped light-wash jeans", "BOTTOM", "light blue");
    private static final WardrobeEntry COAT = item("Camel wool coat", "OUTERWEAR", "camel");
    private static final List<WardrobeEntry> WARDROBE =
            List.of(TROUSERS, SHIRT, SEQUIN_DRESS, STILETTOS, LOAFERS, HOODIE, JEANS, COAT);

    @Autowired
    private StylistClient stylist;

    @Test
    void theStyleSteersTheChoice() {
        assumeTrue(stylist.isConfigured(), "ANTHROPIC_API_KEY not set in .env, skipping");

        List<Suggestion> outfits = stylist.suggest(
                new SuggestionRequest(WARDROBE, "dinner with friends", null, NOTES)).suggestions();

        print("Style notes + \"dinner with friends\"", outfits);
        assertThat(outfits).isNotEmpty();
        assertThat(outfits).allSatisfy(o -> assertThat(o.itemIds()).doesNotContain(STILETTOS.id(), HOODIE.id()));
        // The notes are background, not something to quote back at the user.
        assertThat(outfits).allSatisfy(o -> assertThat(o.reasoning().toLowerCase()).doesNotContain("notes"));
    }

    @Test
    void whatTheyAskForWinsForThatRequest() {
        assumeTrue(stylist.isConfigured(), "ANTHROPIC_API_KEY not set in .env, skipping");

        List<Suggestion> outfits = stylist.suggest(new SuggestionRequest(WARDROBE,
                "Something glam and sparkly for a party tonight, heels are fine for once", null, NOTES)).suggestions();

        print("Style notes + \"glam and sparkly party, heels fine\"", outfits);
        assertThat(outfits).anySatisfy(o -> assertThat(o.itemIds()).contains(SEQUIN_DRESS.id()));
    }

    private static WardrobeEntry item(String name, String category, String color) {
        return new WardrobeEntry(UUID.randomUUID(), name, category, color, List.of(), null);
    }

    private static void print(String title, List<Suggestion> outfits) {
        Map<UUID, String> names = WARDROBE.stream().collect(Collectors.toMap(WardrobeEntry::id, WardrobeEntry::name));
        System.out.printf("%n=== %s ===%n", title);
        for (Suggestion o : outfits) {
            System.out.printf("- %s: %s%n  %s%n", o.title(),
                    o.itemIds().stream().map(names::get).collect(Collectors.joining(", ")), o.reasoning());
        }
        System.out.println();
    }
}
