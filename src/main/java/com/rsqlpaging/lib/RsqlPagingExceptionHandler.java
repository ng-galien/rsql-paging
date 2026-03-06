package com.rsqlpaging.lib;

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

    @ExceptionHandler(RSQLParserException.class)
    public ProblemDetail handleRsqlParseError(RSQLParserException ex) {
        return problemDetail("Invalid RSQL filter", "Invalid RSQL filter: " + ex.getMessage());
    }

    private static ProblemDetail problemDetail(String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(title);
        return problem;
    }
}
