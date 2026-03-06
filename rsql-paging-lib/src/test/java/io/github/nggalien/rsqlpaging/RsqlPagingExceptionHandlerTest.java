/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import static org.assertj.core.api.Assertions.assertThat;

import cz.jirutka.rsql.parser.RSQLParserException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RsqlPagingExceptionHandlerTest {

    private final RsqlPagingExceptionHandler handler = new RsqlPagingExceptionHandler();

    @Test
    void handleIllegalArgument_shouldReturn400() {
        var result = handler.handleIllegalArgument(new IllegalArgumentException("bad param"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Invalid request parameter");
        assertThat(result.getDetail()).isEqualTo("bad param");
    }

    @Test
    void handleUnsupported_shouldReturn400() {
        var result = handler.handleUnsupported(new UnsupportedOperationException("not supported"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Unsupported operation");
        assertThat(result.getDetail()).isEqualTo("not supported");
    }

    @Test
    void handleIllegalState_shouldReturn413() {
        var result = handler.handleIllegalState(new IllegalStateException("too many IDs"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(result.getTitle()).isEqualTo("Query too large");
        assertThat(result.getDetail()).isEqualTo("too many IDs");
    }

    @Test
    void handleRsqlParseError_shouldReturn400() {
        var cause = new cz.jirutka.rsql.parser.ParseException("unexpected token");
        var result = handler.handleRsqlParseError(new RSQLParserException(cause));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Invalid RSQL filter");
        assertThat(result.getDetail()).startsWith("Invalid RSQL filter:");
    }
}
