package com.example.nebullamasearch.search;

public class OllamaChatException extends RuntimeException {

  public OllamaChatException(String message) {
    super(message);
  }

  public OllamaChatException(String message, Throwable cause) {
    super(message, cause);
  }
}
