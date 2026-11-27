package com.trading.platform.exception;

public class DuplicateWatchlistNameException extends RuntimeException {
    public DuplicateWatchlistNameException(String message) {
        super(message);
    }
}
