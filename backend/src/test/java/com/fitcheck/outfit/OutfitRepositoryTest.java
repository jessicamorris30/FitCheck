package com.fitcheck.outfit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Outfits against the real Postgres schema. Each test writes, then flushes and clears so the
 * next read really comes from the database, and is rolled back at the end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutfitRepositoryTest {

    @Autowired
    private OutfitRepository outfits;

    @Autowired
    private TestEntityManager em;

    private User alice;
    private ClothingItem tee;
    private ClothingItem jeans;
    private ClothingItem sneakers;
    private ClothingItem jacket;

    @BeforeEach
    void setUp() {
        alice = em.persist(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        tee = item("White tee", ClothingCategory.TOP);
        jeans = item("Blue jeans", ClothingCategory.BOTTOM);
        sneakers = item("Sneakers", ClothingCategory.SHOES);
        jacket = item("Denim jacket", ClothingCategory.OUTERWEAR);
    }

    private ClothingItem item(String name, ClothingCategory category) {
        return em.persist(new ClothingItem(alice, name, category, null, List.of(),
                "users/" + alice.getId() + "/" + UUID.randomUUID() + ".jpg"));
    }

    /** Write everything to the database and forget the in-memory copies. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Outfit reload(UUID id) {
        return outfits.findByIdAndOwner_Id(id, alice.getId()).orElseThrow();
    }

    @Test
    void savesAnOutfitWithItemsInOrder() {
        Outfit outfit = new Outfit(alice, " Weekend casual ", "  ");
        outfit.replaceItems(List.of(tee, jeans, sneakers));
        outfits.save(outfit);
        flushAndClear();

        Outfit loaded = reload(outfit.getId());
        assertThat(loaded.getName()).isEqualTo("Weekend casual");
        assertThat(loaded.getNotes()).isNull();
        assertThat(loaded.getClothingItems()).extracting(ClothingItem::getName)
                .containsExactly("White tee", "Blue jeans", "Sneakers");
    }

    @Test
    void replacingItemsCanReorderRemoveAndAddInOneSave() {
        Outfit outfit = new Outfit(alice, "Look", null);
        outfit.replaceItems(List.of(tee, jeans, sneakers));
        outfits.save(outfit);
        flushAndClear();

        // Keep jeans + tee (swapped), drop sneakers, add the jacket. With a naive clear-and-re-add
        // this flush would fail with a duplicate key error.
        Outfit loaded = reload(outfit.getId());
        loaded.replaceItems(List.of(jeans, tee, jacket));
        flushAndClear();

        assertThat(reload(outfit.getId()).getClothingItems()).extracting(ClothingItem::getName)
                .containsExactly("Blue jeans", "White tee", "Denim jacket");
    }

    @Test
    void sameItemTwiceIsOnlyAddedOnce() {
        Outfit outfit = new Outfit(alice, "Look", null);
        outfit.replaceItems(List.of(tee, tee, jeans));
        outfits.save(outfit);
        flushAndClear();

        assertThat(reload(outfit.getId()).getClothingItems()).hasSize(2);
    }

    @Test
    void deletingAClothingItemRemovesItFromOutfits() {
        Outfit outfit = new Outfit(alice, "Look", null);
        outfit.replaceItems(List.of(tee, jeans));
        outfits.save(outfit);
        flushAndClear();

        em.remove(em.find(ClothingItem.class, jeans.getId())); // ON DELETE CASCADE does the rest
        flushAndClear();

        assertThat(reload(outfit.getId()).getClothingItems()).extracting(ClothingItem::getName)
                .containsExactly("White tee");
    }

    @Test
    void deletingAnOutfitKeepsTheClothes() {
        Outfit outfit = new Outfit(alice, "Look", null);
        outfit.replaceItems(List.of(tee, jeans));
        outfits.save(outfit);
        flushAndClear();

        outfits.delete(reload(outfit.getId()));
        flushAndClear();

        assertThat(outfits.findById(outfit.getId())).isEmpty();
        assertThat(em.find(ClothingItem.class, tee.getId())).isNotNull();
    }

    @Test
    void usersOnlySeeTheirOwnOutfits() {
        User bob = em.persist(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
        Outfit alices = outfits.save(new Outfit(alice, "Alice's look", null));
        outfits.save(new Outfit(bob, "Bob's look", null));
        flushAndClear();

        assertThat(outfits.findAllByOwner_IdOrderByUpdatedAtDesc(alice.getId()))
                .extracting(Outfit::getName).containsExactly("Alice's look");
        assertThat(outfits.findByIdAndOwner_Id(alices.getId(), bob.getId())).isEmpty();
    }
}
