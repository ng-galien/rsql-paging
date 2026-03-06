package io.github.nggalien.rsqlpaging;

import static org.assertj.core.api.Assertions.assertThat;

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
    void zeroValue_shouldFallbackToDefault() {
        var props = new RsqlPagingProperties(0);
        assertThat(props.maxIdCount()).isEqualTo(1_000_000);
    }

    @Test
    void negativeValue_shouldFallbackToDefault() {
        var props = new RsqlPagingProperties(-1);
        assertThat(props.maxIdCount()).isEqualTo(1_000_000);
    }
}
