package com.mygrinlog.pet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PetImageUrlBuilderTest {

    private final PetProperties properties = new PetProperties(
            new PetProperties.Exp(2, 5, 10),
            new PetProperties.Level(2, 10),
            new PetProperties.ConditionDays(1, 3),
            new PetProperties.PetImage("/assets/giraffe", 4)
    );
    private final PetImageUrlBuilder builder = new PetImageUrlBuilder(properties);

    @Test
    void url_follows_convention_naming() {
        // 스펙 §2.4: {CDN_BASE}/lvl{level}_{condition}_{frame}.png
        assertThat(builder.url(0, PetState.Condition.good, 1))
                .isEqualTo("/assets/giraffe/lvl0_good_1.png");
        assertThat(builder.url(2, PetState.Condition.terrible, 4))
                .isEqualTo("/assets/giraffe/lvl2_terrible_4.png");
    }

    @Test
    void urls_returns_array_of_4_frames() {
        assertThat(builder.urls(1, PetState.Condition.bad))
                .containsExactly(
                        "/assets/giraffe/lvl1_bad_1.png",
                        "/assets/giraffe/lvl1_bad_2.png",
                        "/assets/giraffe/lvl1_bad_3.png",
                        "/assets/giraffe/lvl1_bad_4.png"
                );
    }
}
