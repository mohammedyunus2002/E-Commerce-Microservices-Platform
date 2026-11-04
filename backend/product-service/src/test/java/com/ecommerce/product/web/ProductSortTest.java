package com.ecommerce.product.web;

import com.ecommerce.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSortTest {

    @Test
    void defaultsToNewestFirstWithIdTiebreaker() {
        assertThat(ProductSort.parse(null))
                .containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    @Test
    void parsesFieldAndDirection() {
        assertThat(ProductSort.parse("price,desc"))
                .containsExactly(Sort.Order.desc("price"), Sort.Order.asc("id"));
        assertThat(ProductSort.parse("NAME"))
                .containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
        assertThat(ProductSort.parse("newest"))
                .containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash", "price,sideways", "price,asc,extra", "category.id"})
    void rejectsUnsupportedSorts(String raw) {
        assertThatThrownBy(() -> ProductSort.parse(raw))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_SORT");
    }
}
