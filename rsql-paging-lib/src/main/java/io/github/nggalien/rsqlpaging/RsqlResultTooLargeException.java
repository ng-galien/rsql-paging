/*
 * Copyright (c) 2026 Alexandre Boyer
 * SPDX-License-Identifier: MIT
 */
package io.github.nggalien.rsqlpaging;

/** Thrown when an RSQL query returns more IDs than the configured limit. */
@SuppressWarnings("serial")
public class RsqlResultTooLargeException extends RuntimeException {

    public RsqlResultTooLargeException(String message) {
        super(message);
    }
}
