package com.trading.platform.exception;

public class WatchListItemNotFoundException extends RuntimeException {
    public WatchListItemNotFoundException(String message) {
        super(message);
    }
}
