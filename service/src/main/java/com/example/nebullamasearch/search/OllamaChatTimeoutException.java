package com.example.nebullamasearch.search;

public class OllamaChatTimeoutException extends RuntimeException {

  public OllamaChatTimeoutException(String message) {
    super(message);
  }

  public OllamaChatTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
