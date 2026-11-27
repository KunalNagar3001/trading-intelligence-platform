package com.trading.platform.exception;

public class DuplicateWatchListItemException extends RuntimeException {
  public DuplicateWatchListItemException(String message) {
    super(message);
  }
}