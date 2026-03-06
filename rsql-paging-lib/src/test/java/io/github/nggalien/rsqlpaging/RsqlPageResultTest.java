/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RsqlPageResultTest {

    @Test
    void of_shouldComputeTotalPagesAndFlags() {
        var result = RsqlPageResult.of(List.of("a", "b"), 0, 2, 5);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isFalse();
    }

    @Test
    void of_lastPage_shouldSetLastTrue() {
        var result = RsqlPageResult.of(List.of("e"), 2, 2, 5);

        assertThat(result.last()).isTrue();
        assertThat(result.first()).isFalse();
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void of_singlePage_shouldBeFirstAndLast() {
        var result = RsqlPageResult.of(List.of("a"), 0, 10, 1);

        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void empty_shouldReturnEmptyContent() {
        var result = RsqlPageResult.<String>empty(0, 10, 42);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(42);
        assertThat(result.totalPages()).isEqualTo(5);
    }

    @Test
    void empty_withZeroTotal_shouldHaveZeroPages() {
        var result = RsqlPageResult.<String>empty(0, 10, 0);

        assertThat(result.totalPages()).isZero();
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }

    @Test
    void constructor_shouldRejectNegativePage() {
        assertThatThrownBy(() -> RsqlPageResult.of(List.of(), -1, 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must be >= 0");
    }

    @Test
    void constructor_shouldRejectZeroSize() {
        assertThatThrownBy(() -> RsqlPageResult.of(List.of(), 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be >= 1");
    }

    @Test
    void constructor_shouldRejectNegativeTotalElements() {
        assertThatThrownBy(() -> new RsqlPageResult<>(List.of(), 0, 1, -1, 0, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalElements must be >= 0");
    }

    @Test
    void content_shouldBeImmutableCopy() {
        var mutable = new java.util.ArrayList<>(List.of("a", "b"));
        var result = RsqlPageResult.of(mutable, 0, 10, 2);
        mutable.add("c");

        assertThat(result.content()).containsExactly("a", "b");
    }

    @Test
    void content_shouldBeUnmodifiable() {
        var result = RsqlPageResult.of(List.of("a"), 0, 10, 1);

        assertThatThrownBy(() -> result.content().add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void of_exactlyFull_shouldBeLastPage() {
        var result = RsqlPageResult.of(List.of("a", "b"), 1, 2, 4);

        assertThat(result.last()).isTrue();
        assertThat(result.totalPages()).isEqualTo(2);
    }
}
