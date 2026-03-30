package com.example.nebullamasearch.ingest;

public record IngestResult(String id, boolean success, String error) {

  public static IngestResult ok(String id) {
    return new IngestResult(id, true, null);
  }

  public static IngestResult failed(String id, String error) {
    return new IngestResult(id, false, error);
  }
}
