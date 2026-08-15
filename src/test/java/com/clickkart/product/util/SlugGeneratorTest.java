// src/test/java/com/clickkart/category/util/SlugGeneratorTest.java
package com.clickkart.product.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void aPlainNameBecomesLowercaseAndHyphenated() {
        assertThat(SlugGenerator.slugify("Mobile Phones")).isEqualTo("mobile-phones");
    }

    @Test
    void accentedLettersKeepTheirBaseCharacterRatherThanBeingDropped() {
        // Naive stripping turns "Café" into "caf" and loses a letter; decomposing first keeps it.
        assertThat(SlugGenerator.slugify("Café Accessories")).isEqualTo("cafe-accessories");
    }

    @Test
    void punctuationAndRunsOfSpacesCollapseToSingleHyphens() {
        assertThat(SlugGenerator.slugify("Men's  Shoes & Bags")).isEqualTo("men-s-shoes-bags");
        assertThat(SlugGenerator.slugify("  Leading and trailing  ")).isEqualTo("leading-and-trailing");
        assertThat(SlugGenerator.slugify("--already--hyphenated--")).isEqualTo("already-hyphenated");
    }

    @Test
    void digitsSurvive() {
        assertThat(SlugGenerator.slugify("5G Routers")).isEqualTo("5g-routers");
    }

    @Test
    void aNameInANonLatinScriptYieldsNothingUsable() {
        // Callers must handle this rather than assume a slug always comes back - the service asks
        // the operator to supply one explicitly instead of storing an empty string.
        assertThat(SlugGenerator.slugify("मोबाइल")).isEmpty();
        assertThat(SlugGenerator.slugify("!!!")).isEmpty();
    }

    @Test
    void nullIsTreatedAsEmptyRatherThanThrowing() {
        assertThat(SlugGenerator.slugify(null)).isEmpty();
    }
}
