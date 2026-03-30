/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RsqlPagingPropertiesTest {

    @Test
    void defaultConstructor_shouldUseDefaultMaxIdCount() {
        var props = new RsqlPagingProperties();
        assertThat(props.maxIdCount()).isEqualTo(1_000_000);
    }

    @Test
    void customValue_shouldBeUsed() {
        var props = new RsqlPagingProperties(500_000);
        assertThat(props.maxIdCount()).isEqualTo(500_000);
    }

    @Test
    void zeroValue_shouldThrow() {
        assertThatThrownBy(() -> new RsqlPagingProperties(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be > 0");
    }

    @Test
    void negativeValue_shouldThrow() {
        assertThatThrownBy(() -> new RsqlPagingProperties(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be > 0");
    }
}
