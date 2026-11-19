package com.fitcheck.clothing;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Runs against the real Postgres schema; every test is rolled back afterwards.
 *
 * <p>TestEntityManager lets us "flush and clear": write to the database, then forget the
 * in-memory copies, so the next read really comes back from Postgres (proving the
 * TEXT[] and enum columns round-trip correctly).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClothingItemRepositoryTest {

    @Autowired
    private ClothingItemRepository items;

    @Autowired
    private TestEntityManager em;

    private User alice;
    private User bob;

    @BeforeEach
    void createUsers() {
        alice = em.persist(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        bob = em.persist(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
    }

    private ClothingItem save(User owner, String name, ClothingCategory category, List<String> tags) {
        return items.save(new ClothingItem(owner, name, category, "blue", tags,
                "users/" + owner.getId() + "/" + UUID.randomUUID() + ".jpg"));
    }

    @Test
    void savesAndReloadsAllFieldsIncludingTagsArray() {
        UUID id = save(alice, " Blue jeans ", ClothingCategory.BOTTOM, List.of("casual", "denim")).getId();
        em.flush();
        em.clear();

        ClothingItem loaded = items.findById(id).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Blue jeans");
        assertThat(loaded.getCategory()).isEqualTo(ClothingCategory.BOTTOM);
        assertThat(loaded.getColor()).isEqualTo("blue");
        assertThat(loaded.getTags()).containsExactly("casual", "denim");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void usersOnlySeeTheirOwnItems() {
        save(alice, "Alice shirt", ClothingCategory.TOP, List.of());
        save(alice, "Alice shoes", ClothingCategory.SHOES, List.of());
        ClothingItem bobs = save(bob, "Bob coat", ClothingCategory.OUTERWEAR, List.of());

        assertThat(items.findAllByOwner_IdOrderByCreatedAtDesc(alice.getId()))
                .extracting(ClothingItem::getName)
                .containsExactlyInAnyOrder("Alice shirt", "Alice shoes");
        assertThat(items.countByOwner_Id(bob.getId())).isEqualTo(1);

        // Alice asking for Bob's item by its id gets nothing.
        assertThat(items.findByIdAndOwner_Id(bobs.getId(), alice.getId())).isEmpty();
        assertThat(items.findByIdAndOwner_Id(bobs.getId(), bob.getId())).isPresent();
    }

    @Test
    void filtersByCategory() {
        save(alice, "Tee", ClothingCategory.TOP, List.of());
        save(alice, "Sneakers", ClothingCategory.SHOES, List.of());
        save(alice, "Boots", ClothingCategory.SHOES, List.of());

        assertThat(items.findAllByOwner_IdAndCategoryOrderByCreatedAtDesc(alice.getId(), ClothingCategory.SHOES))
                .extracting(ClothingItem::getName)
                .containsExactlyInAnyOrder("Sneakers", "Boots");
    }

    @Test
    void tagsAreCleanedUp() {
        ClothingItem item = save(alice, "Dress", ClothingCategory.DRESS,
                java.util.Arrays.asList(" Summer ", "summer", "", null, "WORK"));

        assertThat(item.getTags()).containsExactly("summer", "work");
    }

    @Test
    void blankColorIsStoredAsNull() {
        ClothingItem item = new ClothingItem(alice, "Hat", ClothingCategory.ACCESSORY, "  ", null,
                "users/x/" + UUID.randomUUID() + ".jpg");

        assertThat(item.getColor()).isNull();
        assertThat(item.getTags()).isEmpty();
    }
}
