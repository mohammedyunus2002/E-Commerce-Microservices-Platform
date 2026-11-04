package com.ecommerce.product.service;

import com.ecommerce.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugsTest {

    @Test
    void buildsUrlSafeSlugs() {
        assertThat(Slugs.of("Home & Kitchen")).isEqualTo("home-kitchen");
        assertThat(Slugs.of("  Café Accessories!  ")).isEqualTo("cafe-accessories");
        assertThat(Slugs.of("4K TVs")).isEqualTo("4k-tvs");
    }

    @Test
    void rejectsNamesWithoutLettersOrDigits() {
        assertThatThrownBy(() -> Slugs.of("!!!")).isInstanceOf(ApiException.class);
    }
}
