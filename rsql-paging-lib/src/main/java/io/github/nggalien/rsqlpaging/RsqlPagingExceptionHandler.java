/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

import cz.jirutka.rsql.parser.RSQLParserException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RsqlPagingExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problemDetail("Invalid request parameter", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ProblemDetail handleUnsupported(UnsupportedOperationException ex) {
        return problemDetail("Unsupported operation", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problemDetail("Query too large", ex.getMessage(), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(RSQLParserException.class)
    public ProblemDetail handleRsqlParseError(RSQLParserException ex) {
        return problemDetail("Invalid RSQL filter", "Invalid RSQL filter: " + ex.getMessage());
    }

    private static ProblemDetail problemDetail(String title, String detail) {
        return problemDetail(title, detail, HttpStatus.BAD_REQUEST);
    }

    private static ProblemDetail problemDetail(String title, String detail, HttpStatus status) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
